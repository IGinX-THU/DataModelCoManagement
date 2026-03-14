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

/**
 * 分析任务服务实现，提供任务曲线查询、对比、导出与报告生成能力。
 */
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

    /**
     * 查询任务输出序列。
     *
     * @param taskId 任务 ID
     * @param request 查询请求
     * @return 序列列表
     */
    @Override
    public List<TaskSeriesVO> queryTaskSeries(String taskId, TaskSeriesRequest request) {
        TaskEntity task = findTask(taskId);
        boolean relative = request != null && request.isRelative();
        return loadSeriesForTask(task, relative);
    }

    /**
     * 对比多个任务的输出序列。
     *
     * @param request 对比请求
     * @return 序列列表
     */
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

    /**
     * 导出任务数据包（元数据、输入/输出数据、模型文件等）。
     *
     * @param taskId 任务 ID
     * @param request 导出请求
     * @return 下载地址
     */
    @Override
    public String exportPackage(String taskId, TaskExportRequest request) {
        TaskEntity task = findTask(taskId);
        AssociationRuleEntity rule = associationRuleRepository.findById(task.getRuleId())
            .orElseThrow(() -> BizException.badRequest("关联规则不存在"));
        ModelAssetEntity asset = modelAssetRepository.findById(rule.getModelId()).orElse(null);

        // 解析输入/输出绑定关系
        Map<String, String> inputBindings = parseInputBindings(rule.getMappingJson());
        Map<String, String> outputBindings = parseOutputBindings(rule.getOutputTarget());
        Map<String, String> outputPaths = outputBindings;

        List<String> inputPaths = inputBindings.values().stream()
            .filter(StringUtils::hasText)
            .toList();
        List<String> outputPathList = outputPaths.values().stream()
            .filter(StringUtils::hasText)
            .toList();

        // 导出格式默认 CSV
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

    /**
     * 生成任务报告并保存为 PDF。
     *
     * @param taskId 任务 ID
     * @param request 报告请求
     * @return 下载地址
     */
    @Override
    public String generateReport(String taskId, TaskReportRequest request) {
        TaskEntity task = findTask(taskId);
        AssociationRuleEntity rule = associationRuleRepository.findById(task.getRuleId())
            .orElseThrow(() -> BizException.badRequest("关联规则不存在"));
        ModelAssetEntity asset = modelAssetRepository.findById(rule.getModelId()).orElse(null);

        Map<String, String> outputBindings = parseOutputBindings(rule.getOutputTarget());
        Map<String, String> outputPaths = outputBindings;
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

    /**
     * 加载任务输出序列数据。
     *
     * @param task 任务实体
     * @param relative 是否使用相对时间
     * @return 序列列表
     */
    private List<TaskSeriesVO> loadSeriesForTask(TaskEntity task, boolean relative) {
        AssociationRuleEntity rule = associationRuleRepository.findById(task.getRuleId()).orElse(null);
        Map<String, String> ruleOutputs = rule == null ? Map.of() : parseOutputBindings(rule.getOutputTarget());
        Map<String, String> outputPaths = ruleOutputs;
        if (outputPaths.isEmpty()) {
            return List.of();
        }

        List<String> pathList = new ArrayList<>(outputPaths.values());
        long startNs = toNano(task.getRangeStart());
        long endNs = toNano(task.getRangeEnd());
        SessionQueryDataSet dataSet = safeQuerySeries(pathList, startNs, endNs);

        long[] keys = dataSet == null ? null : dataSet.getKeys();
        List<List<Object>> rows = dataSet == null ? null : dataSet.getValues();
        List<String> dataPaths = dataSet == null ? List.of() : dataSet.getPaths();
        if (isDataSetEmpty(dataSet)) {
            return List.of();
        }
        int size = Math.min(keys.length, rows.size());
        // 相对时间以首个点为基准
        long baseKey = relative ? keys[0] : 0L;

        Map<String, Integer> normalizedIndex = new LinkedHashMap<>();
        for (int i = 0; i < dataPaths.size(); i++) {
            // 对路径去掉 root 前缀，便于兼容匹配
            normalizedIndex.putIfAbsent(normalizeMatchKey(dataPaths.get(i)), i);
        }

        List<TaskSeriesVO> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : outputPaths.entrySet()) {
            String outputName = entry.getKey();
            String path = entry.getValue();
            int index = dataPaths.indexOf(path);
            if (index < 0) {
                // 回退到去 root 前缀的匹配方式
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
                // 相对时间用秒，绝对时间用毫秒
                point.setTimestamp(relative ? (keys[i] - baseKey) / 1_000_000_000 : TimeParser.toMillis(keys[i]));
                point.setValue(toDouble(value));
                points.add(point);
            }
            vo.setPoints(points);
            result.add(vo);
        }
        return result;
    }

    /**
     * 查询时序数据，失败时尝试追加 root 前缀兜底。
     *
     * @param paths 路径列表
     * @param startNs 开始时间（纳秒）
     * @param endNs 结束时间（纳秒）
     * @return 查询结果
     */
    private SessionQueryDataSet safeQuerySeries(List<String> paths, long startNs, long endNs) {
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        try {
            return iginxStorageWrapper.executeWithSession(session -> session.queryData(paths, startNs, endNs));
        } catch (Exception ex) {
            if (needsRootPrefix(paths)) {
                // 部分数据源需要 root 前缀，失败时尝试补前缀重试
                List<String> fallbackPaths = addRootPrefix(paths);
                try {
                    return iginxStorageWrapper.executeWithSession(session -> session.queryData(fallbackPaths, startNs, endNs));
                } catch (Exception ignored) {
                    return null;
                }
            }
            return null;
        }
    }

    /**
     * 解析输出绑定关系。
     *
     * @param outputTargetJson 输出目标 JSON
     * @return 输出映射
     */
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

    /**
     * 解析输入绑定关系。
     *
     * @param mappingJson 映射 JSON
     * @return 输入映射
     */
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

    /**
     * 构建任务导出元数据。
     *
     * @param task 任务实体
     * @param rule 关联规则
     * @param asset 模型资产
     * @param inputPaths 输入路径
     * @param outputPaths 输出路径
     * @return 元数据
     */
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

    /**
     * 将对象序列化为 JSON 字节。
     *
     * @param value 对象
     * @return 字节数组
     */
    private byte[] writeJsonBytes(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw BizException.internal("导出元数据失败: " + e.getMessage());
        }
    }

    /**
     * 查询时序数据，必要时追加 root 前缀重试。
     *
     * @param paths 路径列表
     * @param start 开始时间
     * @param end 结束时间
     * @return 查询结果
     */
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

    /**
     * 构建时序数据字节内容。
     *
     * @param paths 路径列表
     * @param dataSet 数据集
     * @param format 导出格式
     * @return 字节数组
     */
    private byte[] buildSeriesBytes(List<String> paths, SessionQueryDataSet dataSet, String format) {
        if ("JSON".equalsIgnoreCase(format)) {
            return buildSeriesJson(paths, dataSet);
        }
        return buildSeriesCsv(paths, dataSet);
    }

    /**
     * 构建 CSV 格式时序数据。
     *
     * @param paths 路径列表
     * @param dataSet 数据集
     * @return CSV 字节
     */
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
            // 写入表头
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

    /**
     * 构建 JSON 格式时序数据。
     *
     * @param paths 路径列表
     * @param dataSet 数据集
     * @return JSON 字节
     */
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

    /**
     * 解析输出路径，优先使用数据集返回路径。
     *
     * @param fallback 兜底路径
     * @param dataSet 数据集
     * @return 路径列表
     */
    private List<String> resolvePaths(List<String> fallback, SessionQueryDataSet dataSet) {
        if (dataSet != null && dataSet.getPaths() != null && !dataSet.getPaths().isEmpty()) {
            return dataSet.getPaths();
        }
        return fallback == null ? List.of() : fallback;
    }

    /**
     * 构建数据包 README 内容。
     *
     * @param task 任务实体
     * @param rule 关联规则
     * @param asset 模型资产
     * @param entries 文件条目
     * @return README 内容
     */
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

    /**
     * 写入 Zip 包。
     *
     * @param path 目标路径
     * @param entries 文件条目
     */
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

    /**
     * 计算输出序列统计信息。
     *
     * @param dataSet 数据集
     * @return 统计结果
     */
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

    /**
     * 构建报告内容对象。
     *
     * @param task 任务实体
     * @param rule 关联规则
     * @param asset 模型资产
     * @param outputPaths 输出路径
     * @param stats 统计信息
     * @param dataSet 数据集
     * @param request 报告请求
     * @return 报告内容
     */
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

    /**
     * 构建报告指标表格。
     *
     * @param stats 统计信息
     * @return 指标列表
     */
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

    /**
     * 构建报告图表数据（含降采样）。
     *
     * @param dataSet 数据集
     * @return 图表数据
     */
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

    /**
     * 格式化时间，空值使用 "-"。
     *
     * @param time 时间
     * @return 格式化字符串
     */
    private String formatDateTime(LocalDateTime time) {
        if (time == null) {
            return "-";
        }
        return time.format(REPORT_TIME_FORMATTER);
    }

    /**
     * 规范化字段值为字符串。
     *
     * @param value 原始值
     * @return 字符串值
     */
    private String normalizeValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    /**
     * 计算 MD5 十六进制摘要。
     *
     * @param data 数据
     * @return MD5 字符串
     */
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

    /**
     * 统计信息。
     *
     * @param count 数量
     * @param min 最小值
     * @param max 最大值
     * @param avg 平均值
     */
    private record Stats(long count, Double min, Double max, Double avg) {
    }

    /**
     * 判断路径是否需要补 root 前缀。
     *
     * @param paths 路径列表
     * @return 是否需要补前缀
     */
    private boolean needsRootPrefix(List<String> paths) {
        for (String path : paths) {
            if (StringUtils.hasText(path) && !path.startsWith("root.")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 为路径补充 root 前缀。
     *
     * @param paths 路径列表
     * @return 补前缀后的路径列表
     */
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

    /**
     * 规范化路径用于匹配（去除 root 前缀）。
     *
     * @param path 路径
     * @return 规范化后的路径
     */
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

    /**
     * 判断数据集是否为空。
     *
     * @param dataSet 数据集
     * @return 是否为空
     */
    private boolean isDataSetEmpty(SessionQueryDataSet dataSet) {
        if (dataSet == null) {
            return true;
        }
        long[] keys = dataSet.getKeys();
        List<List<Object>> rows = dataSet.getValues();
        return keys == null || keys.length == 0 || rows == null || rows.isEmpty();
    }

    /**
     * 将值转换为 Double。
     *
     * @param value 原始值
     * @return Double 值
     */
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

    /**
     * 安全解析 Double。
     *
     * @param text 文本
     * @return Double 值
     */
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

    /**
     * 将时间转换为纳秒。
     *
     * @param time 时间
     * @return 纳秒
     */
    private long toNano(LocalDateTime time) {
        if (time == null) {
            return 0L;
        }
        long millis = time.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        return TimeParser.toNano(millis);
    }

    /**
     * 获取任务实体，不存在则抛异常。
     *
     * @param taskId 任务 ID
     * @return 任务实体
     */
    private TaskEntity findTask(String taskId) {
        return taskRepository.findById(taskId)
            .orElseThrow(() -> BizException.badRequest("任务不存在，id=" + taskId));
    }
}

