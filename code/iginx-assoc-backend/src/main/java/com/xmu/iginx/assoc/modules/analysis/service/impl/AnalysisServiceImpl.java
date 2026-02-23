package com.xmu.iginx.assoc.modules.analysis.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskCompareRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskExportRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskReportRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskSeriesRequest;
import com.xmu.iginx.assoc.modules.analysis.service.AnalysisService;
import com.xmu.iginx.assoc.modules.analysis.util.ReportPdfBuilder;
import com.xmu.iginx.assoc.modules.analysis.vo.TaskSeriesPointVO;
import com.xmu.iginx.assoc.modules.analysis.vo.TaskSeriesVO;
import com.xmu.iginx.assoc.modules.data.util.CsvUtils;
import com.xmu.iginx.assoc.modules.data.util.DataFileStorageService;
import com.xmu.iginx.assoc.modules.data.util.TimeParser;
import com.xmu.iginx.assoc.modules.model.entity.ModelAssetEntity;
import com.xmu.iginx.assoc.modules.model.repository.ModelAssetRepository;
import com.xmu.iginx.assoc.modules.model.util.ModelFileStorageService;
import com.xmu.iginx.assoc.modules.relation.entity.AssociationRuleEntity;
import com.xmu.iginx.assoc.modules.relation.repository.AssociationRuleRepository;
import com.xmu.iginx.assoc.modules.task.entity.TaskEntity;
import com.xmu.iginx.assoc.modules.task.repository.TaskRepository;
import cn.edu.tsinghua.iginx.session.SessionQueryDataSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class AnalysisServiceImpl implements AnalysisService {

    private static final DateTimeFormatter REPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int REPORT_MAX_POINTS = 600;

    private final TaskRepository taskRepository;
    private final AssociationRuleRepository associationRuleRepository;
    private final IginxStorageWrapper iginxStorageWrapper;
    private final ObjectMapper objectMapper;
    private final ModelAssetRepository modelAssetRepository;
    private final ModelFileStorageService modelFileStorageService;
    private final DataFileStorageService dataFileStorageService;

    @Override
    public List<TaskSeriesVO> queryTaskSeries(String taskId, TaskSeriesRequest request) {
        TaskEntity task = findTask(taskId);
        boolean relative = request != null && request.isRelative();
        return loadSeriesForTask(task, relative);
    }

    @Override
    public List<TaskSeriesVO> compareTasks(TaskCompareRequest request) {
        boolean relative = request != null && "relative".equalsIgnoreCase(request.getMode());
        List<TaskSeriesVO> result = new ArrayList<>();
        for (String taskId : request.getTaskIds()) {
            TaskEntity task = findTask(taskId);
            result.addAll(loadSeriesForTask(task, relative));
        }
        return result;
    }

    @Override
    public String exportPackage(String taskId, TaskExportRequest request) {
        TaskEntity task = findTask(taskId);
        AssociationRuleEntity rule = associationRuleRepository.findById(task.getRuleId())
            .orElseThrow(() -> BizException.badRequest("关联规则不存在"));
        ModelAssetEntity asset = modelAssetRepository.findById(rule.getModelId()).orElse(null);

        Map<String, String> inputBindings = parseInputBindings(rule.getMappingJson());
        Map<String, String> outputBindings = parseOutputBindings(rule.getOutputTarget());
        Map<String, String> taskOutputBindings = buildTaskResultPaths(task.getResultLink(), outputBindings);
        Map<String, String> outputPaths = !taskOutputBindings.isEmpty() ? taskOutputBindings : outputBindings;

        List<String> inputPaths = inputBindings.values().stream()
            .filter(StringUtils::hasText)
            .toList();
        List<String> outputPathList = outputPaths.values().stream()
            .filter(StringUtils::hasText)
            .toList();

        String format = request.getFormat() == null ? "CSV" : request.getFormat().trim().toUpperCase(Locale.ROOT);
        String suffix = "JSON".equals(format) ? "json" : "csv";

        Map<String, byte[]> entries = new LinkedHashMap<>();
        Map<String, Object> meta = buildTaskMetadata(task, rule, asset, inputPaths, outputPathList);
        entries.put("metadata/task.json", writeJsonBytes(meta));

        if (request.isIncludeModel() && asset != null) {
            String fileName = StringUtils.hasText(asset.getFileName()) ? asset.getFileName() : "model.bin";
            try {
                byte[] modelBytes = modelFileStorageService.readAsBytes(asset.getStoragePath(), asset.getFileSize());
                if (modelBytes.length > 0) {
                    entries.put("model/" + fileName, modelBytes);
                }
            } catch (BizException ex) {
                if (ex.getCode() != 400) {
                    throw ex;
                }
            } catch (Exception ex) {
                throw BizException.internal("读取模型文件失败: " + ex.getMessage());
            }
        }

        if (request.isIncludeInput() && !inputPaths.isEmpty()) {
            SessionQueryDataSet dataSet = querySeries(inputPaths, task.getRangeStart(), task.getRangeEnd());
            entries.put("data/input." + suffix, buildSeriesBytes(inputPaths, dataSet, format));
        }
        if (request.isIncludeOutput() && !outputPathList.isEmpty()) {
            SessionQueryDataSet dataSet = querySeries(outputPathList, task.getRangeStart(), task.getRangeEnd());
            entries.put("data/output." + suffix, buildSeriesBytes(outputPathList, dataSet, format));
        }

        String readme = buildPackageReadme(task, rule, asset, entries);
        entries.put("README.txt", readme.getBytes(StandardCharsets.UTF_8));

        DataFileStorageService.StoredFile file = dataFileStorageService.createFile("analysis_package", ".zip");
        writeZip(file.path(), entries);
        return "/api/v1/data/files/" + file.fileName();
    }

    @Override
    public String generateReport(String taskId, TaskReportRequest request) {
        TaskEntity task = findTask(taskId);
        AssociationRuleEntity rule = associationRuleRepository.findById(task.getRuleId())
            .orElseThrow(() -> BizException.badRequest("关联规则不存在"));
        ModelAssetEntity asset = modelAssetRepository.findById(rule.getModelId()).orElse(null);

        Map<String, String> outputBindings = parseOutputBindings(rule.getOutputTarget());
        Map<String, String> taskOutputBindings = buildTaskResultPaths(task.getResultLink(), outputBindings);
        Map<String, String> outputPaths = !taskOutputBindings.isEmpty() ? taskOutputBindings : outputBindings;
        List<String> outputPathList = outputPaths.values().stream()
            .filter(StringUtils::hasText)
            .toList();

        SessionQueryDataSet dataSet = outputPathList.isEmpty()
            ? null
            : querySeries(outputPathList, task.getRangeStart(), task.getRangeEnd());
        Map<String, Stats> stats = request.isIncludeStats() ? calculateStats(dataSet) : Map.of();

        ReportPdfBuilder.ReportContent reportContent = buildReportContent(task, rule, asset, outputPaths, stats, dataSet, request);
        byte[] pdfBytes = new ReportPdfBuilder().build(reportContent);

        DataFileStorageService.StoredFile file = dataFileStorageService.createFile("task_report", ".pdf");
        try {
            Files.write(file.path(), pdfBytes);
        } catch (Exception ex) {
            throw BizException.internal("写入报告文件失败: " + ex.getMessage());
        }
        return "/api/v1/data/files/" + file.fileName();
    }

    private List<TaskSeriesVO> loadSeriesForTask(TaskEntity task, boolean relative) {
        AssociationRuleEntity rule = associationRuleRepository.findById(task.getRuleId()).orElse(null);
        Map<String, String> ruleOutputs = rule == null ? Map.of() : parseOutputBindings(rule.getOutputTarget());
        Map<String, String> outputPaths = ruleOutputs;
        Map<String, String> taskResultPaths = buildTaskResultPaths(task.getResultLink(), ruleOutputs);
        boolean preferTaskResult = !taskResultPaths.isEmpty();
        if (preferTaskResult) {
            outputPaths = taskResultPaths;
        } else if (outputPaths.isEmpty() && StringUtils.hasText(task.getResultLink())) {
            outputPaths = Map.of("result", task.getResultLink());
        }
        if (outputPaths.isEmpty()) {
            return List.of();
        }

        List<String> pathList = new ArrayList<>(outputPaths.values());
        long startNs = toNano(task.getRangeStart());
        long endNs = toNano(task.getRangeEnd());
        List<String> initialPaths = pathList;
        SessionQueryDataSet dataSet = iginxStorageWrapper.executeWithSession(session ->
            session.queryData(initialPaths, startNs, endNs));

        long[] keys = dataSet.getKeys();
        List<List<Object>> rows = dataSet.getValues();
        List<String> dataPaths = dataSet.getPaths();
        if ((keys == null || keys.length == 0 || rows == null || rows.isEmpty())
            && preferTaskResult && !ruleOutputs.isEmpty()) {
            outputPaths = ruleOutputs;
            pathList = new ArrayList<>(outputPaths.values());
            List<String> fallbackPaths = pathList;
            dataSet = iginxStorageWrapper.executeWithSession(session ->
                session.queryData(fallbackPaths, startNs, endNs));
            keys = dataSet.getKeys();
            rows = dataSet.getValues();
            dataPaths = dataSet.getPaths();
        }
        if (keys == null || keys.length == 0 || rows == null || rows.isEmpty()) {
            return List.of();
        }
        int size = Math.min(keys.length, rows.size());
        long baseKey = relative ? keys[0] : 0L;

        Map<String, Integer> normalizedIndex = new LinkedHashMap<>();
        for (int i = 0; i < dataPaths.size(); i++) {
            normalizedIndex.putIfAbsent(normalizeMatchKey(dataPaths.get(i)), i);
        }

        List<TaskSeriesVO> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : outputPaths.entrySet()) {
            String outputName = entry.getKey();
            String path = entry.getValue();
            int index = dataPaths.indexOf(path);
            if (index < 0) {
                Integer mapped = normalizedIndex.get(normalizeMatchKey(path));
                if (mapped != null) {
                    index = mapped;
                }
            }
            if (index < 0) {
                continue;
            }
            TaskSeriesVO vo = new TaskSeriesVO();
            vo.setTaskId(task.getId());
            vo.setLabel(StringUtils.hasText(outputName) ? outputName : path);
            vo.setType("OUTPUT");
            vo.setUnit("-");
            vo.setRelative(relative);

            List<TaskSeriesPointVO> points = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                List<Object> row = rows.get(i);
                Object value = index < row.size() ? row.get(index) : null;
                TaskSeriesPointVO point = new TaskSeriesPointVO();
                point.setTimestamp(relative ? (keys[i] - baseKey) / 1_000_000_000 : TimeParser.toMillis(keys[i]));
                point.setValue(toDouble(value));
                points.add(point);
            }
            vo.setPoints(points);
            result.add(vo);
        }
        return result;
    }

    private Map<String, String> parseOutputBindings(String outputTargetJson) {
        if (!StringUtils.hasText(outputTargetJson)) {
            return Map.of();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(outputTargetJson, new TypeReference<>() {});
            Object pathsObj = root.get("paths");
            Map<String, String> results = new LinkedHashMap<>();
            if (pathsObj instanceof Map<?, ?> paths) {
                for (Map.Entry<?, ?> entry : paths.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        results.put(entry.getKey().toString(), entry.getValue().toString());
                    }
                }
            }
            return results;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, String> buildTaskResultPaths(String resultPrefix, Map<String, String> outputBindings) {
        if (!StringUtils.hasText(resultPrefix) || outputBindings == null || outputBindings.isEmpty()) {
            return Map.of();
        }
        String prefix = resultPrefix.trim();
        Map<String, String> results = new LinkedHashMap<>();
        for (String name : outputBindings.keySet()) {
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String path = prefix.endsWith(".") ? prefix + name : prefix + "." + name;
            results.put(name, path);
        }
        return results;
    }

    private Map<String, String> parseInputBindings(String mappingJson) {
        if (!StringUtils.hasText(mappingJson)) {
            return Map.of();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(mappingJson, new TypeReference<>() {});
            Object mappingsObj = root.get("mappings");
            Map<String, String> bindings = new LinkedHashMap<>();
            if (mappingsObj instanceof List<?> list) {
                for (Object itemObj : list) {
                    if (itemObj instanceof Map<?, ?> item) {
                        Object param = item.get("param");
                        Object sourcePath = item.get("source_path");
                        if (param != null && sourcePath != null) {
                            bindings.put(param.toString(), sourcePath.toString());
                        }
                    }
                }
            }
            return bindings;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, Object> buildTaskMetadata(TaskEntity task,
                                                  AssociationRuleEntity rule,
                                                  ModelAssetEntity asset,
                                                  List<String> inputPaths,
                                                  List<String> outputPaths) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("taskId", task.getId());
        meta.put("ruleId", rule.getId());
        meta.put("ruleName", rule.getName());
        meta.put("modelId", rule.getModelId());
        meta.put("modelVersion", asset == null ? null : asset.getVersion());
        meta.put("modelFile", asset == null ? null : asset.getFileName());
        meta.put("rangeStart", task.getRangeStart());
        meta.put("rangeEnd", task.getRangeEnd());
        meta.put("inputPaths", inputPaths);
        meta.put("outputPaths", outputPaths);
        meta.put("resultPrefix", task.getResultLink());
        return meta;
    }

    private byte[] writeJsonBytes(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw BizException.internal("导出元数据失败: " + e.getMessage());
        }
    }

    private SessionQueryDataSet querySeries(List<String> paths, LocalDateTime start, LocalDateTime end) {
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        long startNs = toNano(start);
        long endNs = toNano(end);
        List<String> primaryPaths = new ArrayList<>(paths);
        SessionQueryDataSet dataSet = iginxStorageWrapper.executeWithSession(session ->
            session.queryData(primaryPaths, startNs, endNs));
        if (isDataSetEmpty(dataSet) && needsRootPrefix(primaryPaths)) {
            List<String> fallbackPaths = addRootPrefix(primaryPaths);
            dataSet = iginxStorageWrapper.executeWithSession(session ->
                session.queryData(fallbackPaths, startNs, endNs));
        }
        return dataSet;
    }

    private byte[] buildSeriesBytes(List<String> paths, SessionQueryDataSet dataSet, String format) {
        if ("JSON".equalsIgnoreCase(format)) {
            return buildSeriesJson(paths, dataSet);
        }
        return buildSeriesCsv(paths, dataSet);
    }

    private byte[] buildSeriesCsv(List<String> paths, SessionQueryDataSet dataSet) {
        List<String> dataPaths = resolvePaths(paths, dataSet);
        long[] keys = dataSet == null ? new long[0] : dataSet.getKeys();
        List<List<Object>> rows = dataSet == null ? List.of() : dataSet.getValues();
        int size = Math.min(keys.length, rows.size());
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            List<String> header = new ArrayList<>();
            header.add("timestamp");
            header.addAll(dataPaths);
            writer.write(header.stream().map(CsvUtils::toCsvValue).reduce((a, b) -> a + "," + b).orElse(""));
            writer.newLine();
            for (int i = 0; i < size; i++) {
                List<Object> row = rows.get(i);
                List<String> values = new ArrayList<>();
                values.add(TimeParser.formatMillis(TimeParser.toMillis(keys[i])));
                for (int j = 0; j < dataPaths.size(); j++) {
                    Object value = j < row.size() ? row.get(j) : null;
                    values.add(normalizeValue(value));
                }
                writer.write(values.stream().map(CsvUtils::toCsvValue).reduce((a, b) -> a + "," + b).orElse(""));
                writer.newLine();
            }
            writer.flush();
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw BizException.internal("导出数据失败: " + ex.getMessage());
        }
    }

    private byte[] buildSeriesJson(List<String> paths, SessionQueryDataSet dataSet) {
        List<String> dataPaths = resolvePaths(paths, dataSet);
        long[] keys = dataSet == null ? new long[0] : dataSet.getKeys();
        List<List<Object>> rows = dataSet == null ? List.of() : dataSet.getValues();
        int size = Math.min(keys.length, rows.size());
        Map<String, Object> root = new LinkedHashMap<>();
        List<Long> timestamps = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            timestamps.add(TimeParser.toMillis(keys[i]));
        }
        root.put("timestamps", timestamps);
        List<Map<String, Object>> series = new ArrayList<>();
        for (int i = 0; i < dataPaths.size(); i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("path", dataPaths.get(i));
            List<String> values = new ArrayList<>();
            for (int rowIndex = 0; rowIndex < size; rowIndex++) {
                List<Object> row = rows.get(rowIndex);
                Object value = i < row.size() ? row.get(i) : null;
                values.add(normalizeValue(value));
            }
            item.put("values", values);
            series.add(item);
        }
        root.put("series", series);
        return writeJsonBytes(root);
    }

    private List<String> resolvePaths(List<String> fallback, SessionQueryDataSet dataSet) {
        if (dataSet != null && dataSet.getPaths() != null && !dataSet.getPaths().isEmpty()) {
            return dataSet.getPaths();
        }
        return fallback == null ? List.of() : fallback;
    }

    private String buildPackageReadme(TaskEntity task,
                                      AssociationRuleEntity rule,
                                      ModelAssetEntity asset,
                                      Map<String, byte[]> entries) {
        StringBuilder builder = new StringBuilder();
        builder.append("导出时间: ").append(TimeParser.formatMillis(System.currentTimeMillis())).append("\n");
        builder.append("任务ID: ").append(task.getId()).append("\n");
        builder.append("规则名称: ").append(rule.getName()).append("\n");
        if (asset != null) {
            builder.append("模型文件: ").append(asset.getFileName()).append("\n");
            builder.append("模型版本: ").append(asset.getVersion()).append("\n");
        }
        builder.append("时间范围: ")
            .append(task.getRangeStart() == null ? "-" : task.getRangeStart())
            .append(" ~ ")
            .append(task.getRangeEnd() == null ? "-" : task.getRangeEnd())
            .append("\n\n");
        builder.append("文件清单:\n");
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String md5 = md5Hex(entry.getValue());
            builder.append(entry.getKey())
                .append(" | size=")
                .append(entry.getValue().length)
                .append(" | md5=")
                .append(md5)
                .append("\n");
        }
        return builder.toString();
    }

    private void writeZip(Path path, Map<String, byte[]> entries) {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zip.putNextEntry(zipEntry);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        } catch (Exception ex) {
            throw BizException.internal("资源包导出失败: " + ex.getMessage());
        }
    }

    private Map<String, Stats> calculateStats(SessionQueryDataSet dataSet) {
        if (dataSet == null || dataSet.getPaths() == null || dataSet.getPaths().isEmpty()) {
            return Map.of();
        }
        List<String> paths = dataSet.getPaths();
        List<List<Object>> rows = dataSet.getValues() == null ? List.of() : dataSet.getValues();
        Map<String, Stats> result = new LinkedHashMap<>();
        for (int i = 0; i < paths.size(); i++) {
            double sum = 0;
            long count = 0;
            Double min = null;
            Double max = null;
            for (List<Object> row : rows) {
                Object value = i < row.size() ? row.get(i) : null;
                Double num = toDouble(value);
                if (num == null) {
                    continue;
                }
                count++;
                sum += num;
                min = min == null ? num : Math.min(min, num);
                max = max == null ? num : Math.max(max, num);
            }
            Double avg = count == 0 ? null : sum / count;
            result.put(paths.get(i), new Stats(count, min, max, avg));
        }
        return result;
    }

    private ReportPdfBuilder.ReportContent buildReportContent(TaskEntity task,
                                                             AssociationRuleEntity rule,
                                                             ModelAssetEntity asset,
                                                             Map<String, String> outputPaths,
                                                             Map<String, Stats> stats,
                                                             SessionQueryDataSet dataSet,
                                                             TaskReportRequest request) {
        ReportPdfBuilder.ReportContent content = new ReportPdfBuilder.ReportContent();
        content.setTitle("Experiment Report");
        content.setGeneratedAt(TimeParser.formatMillis(System.currentTimeMillis()));
        content.setTaskId(task.getId());
        content.setRuleName(rule.getName());
        if (asset != null) {
            content.setModelName(asset.getFileName());
            content.setModelVersion(asset.getVersion());
        }
        content.setExecutor("-");
        content.setCreateTime(formatDateTime(task.getCreateTime()));
        content.setStartTime(formatDateTime(task.getStartTime()));
        content.setEndTime(formatDateTime(task.getEndTime()));
        content.setRangeStart(formatDateTime(task.getRangeStart()));
        content.setRangeEnd(formatDateTime(task.getRangeEnd()));
        content.setOutputPaths(outputPaths == null ? List.of() : new ArrayList<>(outputPaths.values()));
        content.setMetrics(buildReportMetrics(stats));
        if (request != null && request.isIncludeCharts()) {
            content.setChartData(buildChartData(dataSet));
        }
        content.setIncludeStats(request == null || request.isIncludeStats());
        content.setIncludeCharts(request == null || request.isIncludeCharts());
        return content;
    }

    private List<ReportPdfBuilder.MetricRow> buildReportMetrics(Map<String, Stats> stats) {
        if (stats == null || stats.isEmpty()) {
            return List.of();
        }
        List<ReportPdfBuilder.MetricRow> rows = new ArrayList<>();
        for (Map.Entry<String, Stats> entry : stats.entrySet()) {
            Stats stat = entry.getValue();
            rows.add(new ReportPdfBuilder.MetricRow(entry.getKey(), stat.count(), stat.min(), stat.max(), stat.avg()));
        }
        return rows;
    }

    private ReportPdfBuilder.ChartData buildChartData(SessionQueryDataSet dataSet) {
        if (dataSet == null || dataSet.getPaths() == null || dataSet.getPaths().isEmpty()) {
            return null;
        }
        long[] keys = dataSet.getKeys();
        List<List<Object>> rows = dataSet.getValues();
        if (keys == null || keys.length == 0 || rows == null || rows.isEmpty()) {
            return null;
        }
        int size = Math.min(keys.length, rows.size());
        int step = Math.max(1, (int) Math.ceil(size / (double) REPORT_MAX_POINTS));
        List<Long> timestamps = new ArrayList<>();
        List<String> paths = dataSet.getPaths();
        List<List<Double>> seriesValues = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            seriesValues.add(new ArrayList<>());
        }
        Double min = null;
        Double max = null;
        // 对曲线进行下采样，保证 PDF 大小与渲染性能
        for (int i = 0; i < size; i += step) {
            timestamps.add(TimeParser.toMillis(keys[i]));
            List<Object> row = rows.get(i);
            for (int col = 0; col < paths.size(); col++) {
                Object raw = col < row.size() ? row.get(col) : null;
                Double value = toDouble(raw);
                seriesValues.get(col).add(value);
                if (value != null) {
                    min = min == null ? value : Math.min(min, value);
                    max = max == null ? value : Math.max(max, value);
                }
            }
        }
        if (timestamps.isEmpty()) {
            return null;
        }
        List<ReportPdfBuilder.ChartSeries> series = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            series.add(new ReportPdfBuilder.ChartSeries(paths.get(i), seriesValues.get(i)));
        }
        return new ReportPdfBuilder.ChartData(timestamps, series, min, max);
    }

    private String formatDateTime(LocalDateTime time) {
        if (time == null) {
            return "-";
        }
        return time.format(REPORT_TIME_FORMATTER);
    }

    private String normalizeValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    private String md5Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(data);
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            return "-";
        }
    }

    private record Stats(long count, Double min, Double max, Double avg) {
    }

    private boolean needsRootPrefix(List<String> paths) {
        for (String path : paths) {
            if (StringUtils.hasText(path) && !path.startsWith("root.")) {
                return true;
            }
        }
        return false;
    }

    private List<String> addRootPrefix(List<String> paths) {
        List<String> result = new ArrayList<>();
        for (String path : paths) {
            if (!StringUtils.hasText(path)) {
                continue;
            }
            if (path.startsWith("root.")) {
                result.add(path);
            } else {
                result.add("root." + path);
            }
        }
        return result;
    }

    private String normalizeMatchKey(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String trimmed = path.trim();
        if (trimmed.startsWith("root.")) {
            return trimmed.substring("root.".length());
        }
        return trimmed;
    }

    private boolean isDataSetEmpty(SessionQueryDataSet dataSet) {
        if (dataSet == null) {
            return true;
        }
        long[] keys = dataSet.getKeys();
        List<List<Object>> rows = dataSet.getValues();
        return keys == null || keys.length == 0 || rows == null || rows.isEmpty();
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof byte[] bytes) {
            return parseDoubleSafely(new String(bytes));
        }
        if (value instanceof String text) {
            return parseDoubleSafely(text);
        }
        return null;
    }

    private Double parseDoubleSafely(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private long toNano(LocalDateTime time) {
        if (time == null) {
            return 0L;
        }
        long millis = time.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        return TimeParser.toNano(millis);
    }

    private TaskEntity findTask(String taskId) {
        return taskRepository.findById(taskId)
            .orElseThrow(() -> BizException.badRequest("任务不存在，id=" + taskId));
    }
}

