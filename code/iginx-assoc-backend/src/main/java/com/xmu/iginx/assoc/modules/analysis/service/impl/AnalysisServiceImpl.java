package com.xmu.iginx.assoc.modules.analysis.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.edu.tsinghua.iginx.thrift.AggregateType;
import com.xmu.iginx.assoc.common.PageResult;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskCompareRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskExportRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskReportRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskSeriesRequest;
import com.xmu.iginx.assoc.modules.analysis.service.AnalysisService;
import com.xmu.iginx.assoc.modules.analysis.util.ReportPdfBuilder;
import com.xmu.iginx.assoc.modules.analysis.vo.TaskAnalysisResultVO;
import com.xmu.iginx.assoc.modules.analysis.vo.TaskSeriesPointVO;
import com.xmu.iginx.assoc.modules.analysis.vo.TaskSeriesVO;
import com.xmu.iginx.assoc.modules.analysis.vo.TaskStructuredResultVO;
import com.xmu.iginx.assoc.modules.data.util.CsvUtils;
import com.xmu.iginx.assoc.modules.data.util.DataFileStorageService;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredQueryHelper;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredUtils;
import com.xmu.iginx.assoc.modules.data.util.TimeParser;
import com.xmu.iginx.assoc.modules.model.entity.ModelAssetEntity;
import com.xmu.iginx.assoc.modules.model.repository.ModelAssetRepository;
import com.xmu.iginx.assoc.modules.model.util.ModelFileStorageService;
import com.xmu.iginx.assoc.modules.relation.entity.AssociationRuleEntity;
import com.xmu.iginx.assoc.modules.relation.repository.AssociationRuleRepository;
import com.xmu.iginx.assoc.modules.task.entity.TaskEntity;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionBinding;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionSnapshot;
import com.xmu.iginx.assoc.modules.task.repository.TaskRepository;
import cn.edu.tsinghua.iginx.session.QueryDataSet;
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
import java.time.Duration;
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
    private static final int DEFAULT_ANALYSIS_MAX_POINTS = 1200;
    private static final int DEFAULT_STRUCTURED_PAGE_SIZE = 50;
    private static final int MAX_STRUCTURED_PAGE_SIZE = 500;
    private static final long STRUCTURED_RESULT_QUERY_END = Long.MAX_VALUE - 2;

    private final TaskRepository taskRepository;
    private final AssociationRuleRepository associationRuleRepository;
    private final IginxStorageWrapper iginxStorageWrapper;
    private final ObjectMapper objectMapper;
    private final ModelAssetRepository modelAssetRepository;
    private final ModelFileStorageService modelFileStorageService;
    private final DataFileStorageService dataFileStorageService;
    private final IginxStructuredQueryHelper structuredQueryHelper;

    /**
     * 查询任务输出序列。
     *
     * @param taskId 任务 ID
     * @param request 查询请求
     * @return 序列列表
     */
    @Override
    public TaskAnalysisResultVO queryTaskSeries(String taskId, TaskSeriesRequest request) {
        TaskEntity task = findTask(taskId);
        return loadTaskAnalysis(task, buildSeriesOptions(request));
    }

    /**
     * 对比多个任务的输出序列。
     *
     * @param request 对比请求
     * @return 序列列表
     */
    @Override
    public TaskAnalysisResultVO compareTasks(TaskCompareRequest request) {
        AnalysisQueryOptions options = buildCompareOptions(request);
        List<TaskSeriesVO> series = new ArrayList<>();
        for (String taskId : request.getTaskIds()) {
            TaskEntity task = findTask(taskId);
            TaskExecutionSnapshot snapshot = parseExecutionSnapshot(task.getExecutionSnapshot());
            String analysisMode = resolveAnalysisMode(task, snapshot);
            if (!"TIME_SERIES".equals(analysisMode)) {
                throw BizException.badRequest("结构化输入任务仅支持单独查看结果表，不能与其他任务一起对比");
            }
            series.addAll(loadSeriesForTask(task, options));
        }
        TaskAnalysisResultVO result = new TaskAnalysisResultVO();
        result.setAnalysisMode("TIME_SERIES");
        result.setRelative(options.relative());
        result.setSeries(series);
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
        TaskExecutionSnapshot snapshot = parseExecutionSnapshot(task.getExecutionSnapshot());
        String analysisMode = resolveAnalysisMode(task, snapshot);

        // 优先使用任务快照中的真实执行路径，避免规则后续变更影响历史任务导出。
        Map<String, String> inputBindings = resolveInputBindings(task, rule, snapshot);
        Map<String, String> outputPaths = resolveOutputBindings(task, rule, snapshot);

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
        Map<String, Object> meta = buildTaskMetadata(task, rule, asset, inputBindings, outputPaths, analysisMode);
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

        if ("STRUCTURED".equals(analysisMode)) {
            if (request.isIncludeInput() && !inputBindings.isEmpty()) {
                StructuredTableData inputTable = loadStructuredInputTable(task, rule, snapshot);
                entries.put("data/input." + suffix, buildStructuredTableBytes(inputTable, format));
            }
            if (request.isIncludeOutput() && !outputPathList.isEmpty()) {
                StructuredTableData outputTable = loadStructuredResultTableForTask(task, snapshot);
                entries.put("data/output." + suffix, buildStructuredTableBytes(outputTable, format));
            }
        } else {
            if (request.isIncludeInput() && !inputPaths.isEmpty()) {
                SessionQueryDataSet dataSet = querySeries(inputPaths, task.getRangeStart(), task.getRangeEnd());
                entries.put("data/input." + suffix, buildSeriesBytes(inputPaths, dataSet, format));
            }
            if (request.isIncludeOutput() && !outputPathList.isEmpty()) {
                SessionQueryDataSet dataSet = queryOutputSeries(task, outputPathList, analysisMode);
                entries.put("data/output." + suffix, buildSeriesBytes(outputPathList, dataSet, format));
            }
        }

        String readme = buildPackageReadme(task, rule, asset, analysisMode, entries);
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
        TaskExecutionSnapshot snapshot = parseExecutionSnapshot(task.getExecutionSnapshot());
        String analysisMode = resolveAnalysisMode(task, snapshot);

        Map<String, String> outputPaths = resolveOutputBindings(task, rule, snapshot);
        List<String> outputPathList = outputPaths.values().stream()
            .filter(StringUtils::hasText)
            .toList();

        ReportPdfBuilder.ReportContent reportContent;
        if ("STRUCTURED".equals(analysisMode)) {
            StructuredTableData inputTable = loadStructuredInputTable(task, rule, snapshot);
            StructuredTableData outputTable = loadStructuredResultTableForTask(task, snapshot);
            Map<String, Stats> stats = request != null && request.isIncludeStats()
                ? calculateStructuredStats(outputTable)
                : Map.of();
            reportContent = buildStructuredReportContent(task, rule, asset, outputPaths, stats, inputTable, outputTable, request);
        } else {
            SessionQueryDataSet dataSet = outputPathList.isEmpty()
                ? null
                : queryOutputSeries(task, outputPathList, analysisMode);
            Map<String, Stats> stats = request != null && request.isIncludeStats() ? calculateStats(dataSet) : Map.of();
            reportContent = buildTimeSeriesReportContent(task, rule, asset, outputPaths, stats, dataSet, request);
        }
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
    private TaskAnalysisResultVO loadTaskAnalysis(TaskEntity task, AnalysisQueryOptions options) {
        TaskExecutionSnapshot snapshot = parseExecutionSnapshot(task.getExecutionSnapshot());
        String analysisMode = resolveAnalysisMode(task, snapshot);
        TaskAnalysisResultVO result = new TaskAnalysisResultVO();
        result.setAnalysisMode(analysisMode);
        if ("STRUCTURED".equals(analysisMode)) {
            result.setRelative(false);
            result.setSeries(List.of());
            result.setStructuredResult(loadStructuredResultForTask(task, snapshot, options.pageNum(), options.pageSize()));
            return result;
        }
        result.setRelative(options.relative());
        result.setSeries(loadSeriesForTask(task, options));
        return result;
    }

    /**
     * 加载任务折线图数据。
     */
    private List<TaskSeriesVO> loadSeriesForTask(TaskEntity task, AnalysisQueryOptions options) {
        AssociationRuleEntity rule = associationRuleRepository.findById(task.getRuleId()).orElse(null);
        TaskExecutionSnapshot snapshot = parseExecutionSnapshot(task.getExecutionSnapshot());
        Map<String, String> outputPaths = resolveOutputBindings(task, rule, snapshot);
        if (outputPaths.isEmpty()) {
            return List.of();
        }

        List<String> pathList = new ArrayList<>(outputPaths.values());
        Long localDownsamplePrecisionMs = resolveLocalDownsamplePrecisionMs(task, options);
        boolean shouldUseLocalDownsample = false;
        SessionQueryDataSet dataSet;
        try {
            dataSet = queryOutputSeries(task, pathList, "TIME_SERIES", options);
        } catch (BizException ex) {
            if (!shouldFallbackToLocalDownsample(options, localDownsamplePrecisionMs, ex)) {
                throw ex;
            }
            shouldUseLocalDownsample = true;
            dataSet = queryOutputSeries(task, pathList, "TIME_SERIES", null);
        }

        long[] keys = dataSet == null ? null : dataSet.getKeys();
        List<List<Object>> rows = dataSet == null ? null : dataSet.getValues();
        List<String> dataPaths = dataSet == null ? List.of() : dataSet.getPaths();
        if (isDataSetEmpty(dataSet)) {
            return List.of();
        }
        int size = Math.min(keys.length, rows.size());
        // 相对时间以首个点为基准
        long baseKey = options.relative() ? keys[0] : 0L;

        Map<String, Integer> normalizedIndex = new LinkedHashMap<>();
        for (int i = 0; i < dataPaths.size(); i++) {
            // 对路径去掉 root 前缀，便于兼容匹配
            normalizedIndex.putIfAbsent(normalizeMatchKey(dataPaths.get(i)), i);
        }

        List<TaskSeriesVO> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : outputPaths.entrySet()) {
            String outputName = entry.getKey();
            String path = entry.getValue();
            int index = resolveDataPathIndex(dataPaths, normalizedIndex, path);
            if (index < 0) {
                continue;
            }
            TaskSeriesVO vo = new TaskSeriesVO();
            vo.setTaskId(task.getId());
            vo.setLabel(StringUtils.hasText(outputName) ? outputName : path);
            vo.setType("OUTPUT");
            vo.setUnit("-");
            vo.setRelative(options.relative());

            List<TaskSeriesPointVO> points = shouldUseLocalDownsample
                ? buildLocallyDownsampledPoints(keys, rows, size, index, options, localDownsamplePrecisionMs)
                : buildSeriesPoints(keys, rows, size, index, options.relative(), baseKey);
            vo.setPoints(points);
            result.add(vo);
        }
        return result;
    }

    /**
     * 加载结构化任务结果表。
     */
    private TaskStructuredResultVO loadStructuredResultForTask(TaskEntity task,
                                                               TaskExecutionSnapshot snapshot,
                                                               int pageNum,
                                                               int pageSize) {
        StructuredResultContext context = buildStructuredResultContext(task, snapshot);
        TaskStructuredResultVO result = new TaskStructuredResultVO();
        result.setTaskId(task.getId());
        result.setColumns(context.columns());

        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        if (context.outputPaths().isEmpty()) {
            result.setPage(PageResult.of(List.of(), 0L, safePageNum, safePageSize));
            return result;
        }

        List<String> pathList = new ArrayList<>(context.outputPaths().values());
        long total = queryStructuredResultTotal(pathList);
        long offset = (long) (safePageNum - 1) * safePageSize;
        if (total <= 0 || offset >= total) {
            result.setPage(PageResult.of(List.of(), total, safePageNum, safePageSize));
            return result;
        }

        long endExclusive = Math.min(total, offset + safePageSize);
        SessionQueryDataSet dataSet = safeQuerySeries(pathList, offset, endExclusive);
        result.setPage(PageResult.of(mapStructuredResultRows(dataSet, context.outputPaths()), total, safePageNum, safePageSize));
        return result;
    }

    /**
     * 查询结构化任务结果全集，用于资源导出与报告预览。
     */
    private StructuredTableData loadStructuredResultTableForTask(TaskEntity task, TaskExecutionSnapshot snapshot) {
        StructuredResultContext context = buildStructuredResultContext(task, snapshot);
        if (context.outputPaths().isEmpty()) {
            return new StructuredTableData(context.columns(), List.of());
        }

        List<String> pathList = new ArrayList<>(context.outputPaths().values());
        long total = queryStructuredResultTotal(pathList);
        if (total <= 0) {
            return new StructuredTableData(context.columns(), List.of());
        }
        SessionQueryDataSet dataSet = safeQuerySeries(pathList, 0L, total);
        return new StructuredTableData(context.columns(), mapStructuredResultRows(dataSet, context.outputPaths()));
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
     * 查询最新一个点，失败时尝试补 root 前缀兜底。
     */
    private SessionQueryDataSet safeQueryLast(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        try {
            return iginxStorageWrapper.executeWithSession(session -> session.queryLast(paths, 0L));
        } catch (Exception ex) {
            if (needsRootPrefix(paths)) {
                List<String> fallbackPaths = addRootPrefix(paths);
                try {
                    return iginxStorageWrapper.executeWithSession(session -> session.queryLast(fallbackPaths, 0L));
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
                                                  Map<String, String> inputBindings,
                                                  Map<String, String> outputBindings,
                                                  String analysisMode) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("taskId", task.getId());
        meta.put("ruleId", rule.getId());
        meta.put("ruleName", rule.getName());
        meta.put("modelId", rule.getModelId());
        meta.put("modelVersion", asset == null ? null : asset.getVersion());
        meta.put("modelFile", asset == null ? null : asset.getFileName());
        meta.put("rangeStart", task.getRangeStart());
        meta.put("rangeEnd", task.getRangeEnd());
        meta.put("analysisMode", analysisMode);
        meta.put("inputBindings", inputBindings == null ? Map.of() : inputBindings);
        meta.put("outputBindings", outputBindings == null ? Map.of() : outputBindings);
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
        if (start == null || end == null) {
            SessionQueryDataSet dataSet = iginxStorageWrapper.executeWithSession(session ->
                session.queryLast(primaryPaths, 0L));
            if (isDataSetEmpty(dataSet) && needsRootPrefix(primaryPaths)) {
                List<String> fallbackPaths = addRootPrefix(primaryPaths);
                dataSet = iginxStorageWrapper.executeWithSession(session ->
                    session.queryLast(fallbackPaths, 0L));
            }
            return dataSet;
        }
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
     * 查询时序数据并在服务端执行降采样，必要时追加 root 前缀重试。
     */
    private SessionQueryDataSet querySeriesWithDownsample(List<String> paths,
                                                          LocalDateTime start,
                                                          LocalDateTime end,
                                                          String aggregator,
                                                          Long precisionMs) {
        if (paths == null || paths.isEmpty() || start == null || end == null || precisionMs == null || precisionMs <= 0) {
            return querySeries(paths, start, end);
        }
        long startNs = toNano(start);
        long endNs = toNano(end);
        List<String> primaryPaths = new ArrayList<>(paths);
        SessionQueryDataSet dataSet = iginxStorageWrapper.executeWithSession(session ->
            session.downsampleQuery(primaryPaths, startNs, endNs, mapAggregateType(aggregator), TimeParser.toNano(precisionMs)));
        if (isDataSetEmpty(dataSet) && needsRootPrefix(primaryPaths)) {
            List<String> fallbackPaths = addRootPrefix(primaryPaths);
            dataSet = iginxStorageWrapper.executeWithSession(session ->
                session.downsampleQuery(fallbackPaths, startNs, endNs, mapAggregateType(aggregator), TimeParser.toNano(precisionMs)));
        }
        return dataSet;
    }

    /**
     * 查询任务输出序列。
     * <p>
     * 对于仅绑定 rt.* 输入的任务，任务本身没有业务时间区间，
     * 但输出仍会以“执行时刻”为 KEY 写入 task.result.* 路径。
     * 此时若 queryLast 返回空，需要回退到任务实际执行时间窗口内做一次 queryData，
     * 否则前端会误以为“任务成功但没有结果”。
     * </p>
     */
    private SessionQueryDataSet queryOutputSeries(TaskEntity task, List<String> paths, String analysisMode) {
        return queryOutputSeries(task, paths, analysisMode, null);
    }

    /**
     * 查询任务输出序列，并根据分析参数决定是否做服务端降采样。
     */
    private SessionQueryDataSet queryOutputSeries(TaskEntity task,
                                                  List<String> paths,
                                                  String analysisMode,
                                                  AnalysisQueryOptions options) {
        if ("STRUCTURED".equalsIgnoreCase(analysisMode)) {
            SessionQueryDataSet structuredDataSet = safeQuerySeries(paths, 0L, STRUCTURED_RESULT_QUERY_END);
            if (!isDataSetEmpty(structuredDataSet)) {
                return structuredDataSet;
            }
        }
        SessionQueryDataSet dataSet = queryTimeSeriesOutputSeries(task, paths, options);
        if (!isDataSetEmpty(dataSet)) {
            return dataSet;
        }
        if (task == null || !isOutputWindowFallbackNeeded(task)) {
            return dataSet;
        }
        LocalDateTime fallbackStart = resolveOutputWindowStart(task);
        LocalDateTime fallbackEnd = resolveOutputWindowEnd(task);
        if (fallbackStart == null || fallbackEnd == null) {
            return dataSet;
        }
        long startNs = toNano(fallbackStart.minusSeconds(1));
        long endNs = toNano(fallbackEnd.plusSeconds(1));
        return safeQuerySeries(paths, startNs, endNs);
    }

    /**
     * 查询时序任务结果，必要时启用服务端降采样。
     */
    private SessionQueryDataSet queryTimeSeriesOutputSeries(TaskEntity task,
                                                            List<String> paths,
                                                            AnalysisQueryOptions options) {
        if (options != null && options.downsample()) {
            LocalDateTime start = task == null ? null : task.getRangeStart();
            LocalDateTime end = task == null ? null : task.getRangeEnd();
            Long precisionMs = resolveDownsamplePrecisionMs(start, end, options.precisionMs());
            if (precisionMs != null) {
                SessionQueryDataSet sampled = querySeriesWithDownsample(paths, start, end, options.aggregator(), precisionMs);
                if (!isDataSetEmpty(sampled)) {
                    return sampled;
                }
            }
        }
        return querySeries(paths, task == null ? null : task.getRangeStart(), task == null ? null : task.getRangeEnd());
    }

    /**
     * 构建普通时序点集合。
     */
    private List<TaskSeriesPointVO> buildSeriesPoints(long[] keys,
                                                      List<List<Object>> rows,
                                                      int size,
                                                      int index,
                                                      boolean relative,
                                                      long baseKey) {
        List<TaskSeriesPointVO> points = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            List<Object> row = rows.get(i);
            Object value = index < row.size() ? row.get(index) : null;
            points.add(buildSeriesPoint(keys[i], value, relative, baseKey));
        }
        return points;
    }

    /**
     * 在服务端降采样不可用时，使用本地聚合避免任务结果页直接报错。
     */
    private List<TaskSeriesPointVO> buildLocallyDownsampledPoints(long[] keys,
                                                                  List<List<Object>> rows,
                                                                  int size,
                                                                  int index,
                                                                  AnalysisQueryOptions options,
                                                                  Long precisionMs) {
        if (precisionMs == null || precisionMs <= 0) {
            long baseKey = options != null && options.relative() && size > 0 ? keys[0] : 0L;
            return buildSeriesPoints(keys, rows, size, index, options != null && options.relative(), baseKey);
        }
        long bucketSizeNs = TimeParser.toNano(precisionMs);
        long firstKey = keys[0];
        String aggregator = normalizeAggregator(options == null ? null : options.aggregator());
        List<LocalDownsampleBucket> buckets = new ArrayList<>();
        LocalDownsampleBucket currentBucket = null;
        for (int i = 0; i < size; i++) {
            long key = keys[i];
            long bucketIndex = Math.max(0L, Math.floorDiv(key - firstKey, bucketSizeNs));
            if (currentBucket == null || currentBucket.bucketIndex() != bucketIndex) {
                if (currentBucket != null) {
                    buckets.add(currentBucket);
                }
                currentBucket = new LocalDownsampleBucket(bucketIndex, key);
            }
            List<Object> row = rows.get(i);
            Object value = index < row.size() ? row.get(index) : null;
            currentBucket.add(value, toDouble(value));
        }
        if (currentBucket != null) {
            buckets.add(currentBucket);
        }
        if (buckets.isEmpty()) {
            return List.of();
        }

        boolean relative = options != null && options.relative();
        long baseKey = relative ? buckets.get(0).timestampNs() : 0L;
        List<TaskSeriesPointVO> points = new ArrayList<>();
        for (LocalDownsampleBucket bucket : buckets) {
            TaskSeriesPointVO point = new TaskSeriesPointVO();
            point.setTimestamp(relative
                ? (bucket.timestampNs() - baseKey) / 1_000_000_000
                : TimeParser.toMillis(bucket.timestampNs()));
            point.setValue(bucket.aggregate(aggregator));
            points.add(point);
        }
        return points;
    }

    /**
     * 构建单个时序点。
     */
    private TaskSeriesPointVO buildSeriesPoint(long key, Object value, boolean relative, long baseKey) {
        TaskSeriesPointVO point = new TaskSeriesPointVO();
        // 相对时间用秒，绝对时间用毫秒
        point.setTimestamp(relative ? (key - baseKey) / 1_000_000_000 : TimeParser.toMillis(key));
        point.setValue(toDouble(value));
        return point;
    }

    /**
     * 解析任务分析展示模式。
     * <p>
     * 只要输入中存在 ts.*，就按时序任务处理；否则若存在 rt.*，则按结构化任务处理。
     * 对旧任务快照缺失的情况，回退到业务时间区间是否存在来判断。
     * </p>
     */
    private String resolveAnalysisMode(TaskEntity task, TaskExecutionSnapshot snapshot) {
        if (snapshot != null && snapshot.getInputs() != null && !snapshot.getInputs().isEmpty()) {
            boolean hasTs = snapshot.getInputs().stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(item -> "TS".equalsIgnoreCase(item.getPathKind()));
            if (hasTs) {
                return "TIME_SERIES";
            }
            boolean hasRt = snapshot.getInputs().stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(item -> "RT".equalsIgnoreCase(item.getPathKind()));
            if (hasRt) {
                return "STRUCTURED";
            }
        }
        return task != null && (task.getRangeStart() != null || task.getRangeEnd() != null)
            ? "TIME_SERIES"
            : "STRUCTURED";
    }

    /**
     * 定位输出路径在数据集中的列索引，兼容 root 前缀差异。
     */
    private int resolveDataPathIndex(List<String> dataPaths,
                                     Map<String, Integer> normalizedIndex,
                                     String path) {
        int index = dataPaths.indexOf(path);
        if (index >= 0) {
            return index;
        }
        Integer mapped = normalizedIndex.get(normalizeMatchKey(path));
        return mapped == null ? -1 : mapped;
    }

    /**
     * 将结构化结果单元格规范化为可展示值。
     */
    private Object normalizeStructuredDisplayValue(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value;
    }

    /**
     * 构建结构化结果查询上下文。
     */
    private StructuredResultContext buildStructuredResultContext(TaskEntity task, TaskExecutionSnapshot snapshot) {
        AssociationRuleEntity rule = associationRuleRepository.findById(task.getRuleId()).orElse(null);
        Map<String, String> resolvedOutputPaths = resolveOutputBindings(task, rule, snapshot);
        LinkedHashMap<String, String> outputPaths = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : resolvedOutputPaths.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
                continue;
            }
            outputPaths.put(entry.getKey(), entry.getValue());
        }
        List<String> columns = new ArrayList<>();
        columns.add("KEY");
        columns.addAll(outputPaths.keySet());
        return new StructuredResultContext(outputPaths, columns);
    }

    /**
     * 计算结构化结果总行数。
     */
    private long queryStructuredResultTotal(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return 0L;
        }
        long maxKey = extractMaxKey(safeQueryLast(paths));
        if (maxKey >= 0) {
            return maxKey + 1;
        }
        SessionQueryDataSet dataSet = safeQuerySeries(paths, 0L, STRUCTURED_RESULT_QUERY_END);
        if (isDataSetEmpty(dataSet) || dataSet.getKeys() == null) {
            return 0L;
        }
        return dataSet.getKeys().length;
    }

    /**
     * 提取结果集中最大的 KEY。
     */
    private long extractMaxKey(SessionQueryDataSet dataSet) {
        if (dataSet == null || dataSet.getKeys() == null || dataSet.getKeys().length == 0) {
            return -1L;
        }
        long maxKey = -1L;
        for (long key : dataSet.getKeys()) {
            maxKey = Math.max(maxKey, key);
        }
        return maxKey;
    }

    /**
     * 将结构化结果集映射为页面展示行。
     */
    private List<Map<String, Object>> mapStructuredResultRows(SessionQueryDataSet dataSet,
                                                              LinkedHashMap<String, String> outputPaths) {
        if (isDataSetEmpty(dataSet) || outputPaths == null || outputPaths.isEmpty()) {
            return List.of();
        }
        long[] keys = dataSet.getKeys();
        List<List<Object>> rawRows = dataSet.getValues() == null ? List.of() : dataSet.getValues();
        List<String> dataPaths = dataSet.getPaths() == null ? List.of() : dataSet.getPaths();
        int size = Math.min(keys.length, rawRows.size());

        Map<String, Integer> normalizedIndex = new LinkedHashMap<>();
        for (int index = 0; index < dataPaths.size(); index++) {
            normalizedIndex.putIfAbsent(normalizeMatchKey(dataPaths.get(index)), index);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < size; rowIndex++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("KEY", keys[rowIndex]);
            List<Object> rawRow = rawRows.get(rowIndex);
            for (Map.Entry<String, String> entry : outputPaths.entrySet()) {
                int columnIndex = resolveDataPathIndex(dataPaths, normalizedIndex, entry.getValue());
                Object value = columnIndex >= 0 && columnIndex < rawRow.size() ? rawRow.get(columnIndex) : null;
                row.put(entry.getKey(), normalizeStructuredDisplayValue(value));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 将请求对象归一化为统一分析选项。
     */
    private AnalysisQueryOptions buildSeriesOptions(TaskSeriesRequest request) {
        return new AnalysisQueryOptions(
            request != null && request.isRelative(),
            request == null || request.isDownsample(),
            request == null ? "AVG" : request.getAggregator(),
            request == null ? null : request.getPrecisionMs(),
            safePageNum(request == null ? null : request.getPageNum()),
            safePageSize(request == null ? null : request.getPageSize())
        );
    }

    /**
     * 将对比请求归一化为统一分析选项。
     */
    private AnalysisQueryOptions buildCompareOptions(TaskCompareRequest request) {
        return new AnalysisQueryOptions(
            request != null && "relative".equalsIgnoreCase(request.getMode()),
            request == null || request.isDownsample(),
            request == null ? "AVG" : request.getAggregator(),
            request == null ? null : request.getPrecisionMs(),
            1,
            DEFAULT_STRUCTURED_PAGE_SIZE
        );
    }

    /**
     * 安全读取结构化结果页码。
     */
    private int safePageNum(Integer pageNum) {
        if (pageNum == null || pageNum < 1) {
            return 1;
        }
        return pageNum;
    }

    /**
     * 安全读取结构化结果分页大小。
     */
    private int safePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_STRUCTURED_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_STRUCTURED_PAGE_SIZE);
    }

    /**
     * 解析降采样步长，未显式传入时按任务跨度自动估算。
     */
    private Long resolveDownsamplePrecisionMs(LocalDateTime start, LocalDateTime end, Long requestedPrecisionMs) {
        if (requestedPrecisionMs != null && requestedPrecisionMs > 0) {
            return requestedPrecisionMs;
        }
        if (start == null || end == null || !end.isAfter(start)) {
            return null;
        }
        long durationMs = Math.max(1L, Duration.between(start, end).toMillis());
        return Math.max(1L, (long) Math.ceil(durationMs / (double) DEFAULT_ANALYSIS_MAX_POINTS));
    }

    /**
     * 解析本地降采样需要使用的步长。
     */
    private Long resolveLocalDownsamplePrecisionMs(TaskEntity task, AnalysisQueryOptions options) {
        if (task == null || options == null || !options.downsample()) {
            return null;
        }
        return resolveDownsamplePrecisionMs(task.getRangeStart(), task.getRangeEnd(), options.precisionMs());
    }

    /**
     * 判断是否应从服务端降采样回退到本地聚合。
     */
    private boolean shouldFallbackToLocalDownsample(AnalysisQueryOptions options, Long precisionMs, BizException ex) {
        if (options == null || !options.downsample() || precisionMs == null || precisionMs <= 0) {
            return false;
        }
        return isUnsupportedDownsampleError(ex);
    }

    /**
     * 判断是否属于 IGinX 不支持映射函数导致的降采样失败。
     */
    private boolean isUnsupportedDownsampleError(BizException ex) {
        String message = ex == null ? null : ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String lower = message.trim().toLowerCase(Locale.ROOT);
        return lower.contains("mapping function") || lower.contains("set mapping function");
    }

    /**
     * 归一化聚合器名称，统一本地聚合行为。
     */
    private String normalizeAggregator(String aggregator) {
        if (!StringUtils.hasText(aggregator)) {
            return "AVG";
        }
        return aggregator.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 将聚合器字符串映射为 IGinX 聚合类型。
     */
    private AggregateType mapAggregateType(String aggregator) {
        if (aggregator == null) {
            return AggregateType.AVG;
        }
        return switch (aggregator.trim().toUpperCase(Locale.ROOT)) {
            case "MAX" -> AggregateType.MAX;
            case "MIN" -> AggregateType.MIN;
            case "SUM" -> AggregateType.SUM;
            case "COUNT" -> AggregateType.COUNT;
            case "FIRST" -> AggregateType.FIRST;
            case "LAST" -> AggregateType.LAST;
            default -> AggregateType.AVG;
        };
    }

    /**
     * 判断是否需要按任务执行窗口回查输出点。
     */
    private boolean isOutputWindowFallbackNeeded(TaskEntity task) {
        return task != null
            && task.getRangeStart() == null
            && task.getRangeEnd() == null
            && (task.getStartTime() != null || task.getEndTime() != null || task.getCreateTime() != null);
    }

    /**
     * 解析任务输出查询的开始时间。
     */
    private LocalDateTime resolveOutputWindowStart(TaskEntity task) {
        if (task == null) {
            return null;
        }
        if (task.getStartTime() != null) {
            return task.getStartTime();
        }
        if (task.getCreateTime() != null) {
            return task.getCreateTime();
        }
        return task.getEndTime();
    }

    /**
     * 解析任务输出查询的结束时间。
     */
    private LocalDateTime resolveOutputWindowEnd(TaskEntity task) {
        if (task == null) {
            return null;
        }
        if (task.getEndTime() != null) {
            return task.getEndTime();
        }
        if (task.getStartTime() != null) {
            return task.getStartTime();
        }
        return task.getCreateTime();
    }

    /**
     * 解析任务执行快照。
     */
    private TaskExecutionSnapshot parseExecutionSnapshot(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, TaskExecutionSnapshot.class);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 解析任务输入绑定，优先读取任务快照。
     */
    private Map<String, String> resolveInputBindings(TaskEntity task,
                                                     AssociationRuleEntity rule,
                                                     TaskExecutionSnapshot snapshot) {
        if (snapshot != null && snapshot.getInputs() != null && !snapshot.getInputs().isEmpty()) {
            Map<String, String> result = new LinkedHashMap<>();
            for (TaskExecutionBinding item : snapshot.getInputs()) {
                if (item == null || !StringUtils.hasText(item.getName()) || !StringUtils.hasText(item.getResolvedPath())) {
                    continue;
                }
                result.put(item.getName(), item.getResolvedPath());
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        return rule == null ? Map.of() : parseInputBindings(rule.getMappingJson());
    }

    /**
     * 解析任务输出绑定，优先读取任务快照。
     */
    private Map<String, String> resolveOutputBindings(TaskEntity task,
                                                      AssociationRuleEntity rule,
                                                      TaskExecutionSnapshot snapshot) {
        if (snapshot != null && snapshot.getOutputs() != null && !snapshot.getOutputs().isEmpty()) {
            Map<String, String> result = new LinkedHashMap<>();
            for (TaskExecutionBinding item : snapshot.getOutputs()) {
                if (item == null || !StringUtils.hasText(item.getName()) || !StringUtils.hasText(item.getResolvedPath())) {
                    continue;
                }
                result.put(item.getName(), item.getResolvedPath());
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        if (rule == null) {
            return Map.of();
        }
        Map<String, String> parsed = parseOutputBindings(rule.getOutputTarget());
        if (parsed.isEmpty()) {
            return parsed;
        }
        String defaultPrefix = StringUtils.hasText(task.getResultLink())
            ? task.getResultLink().trim()
            : "task.result." + task.getId();
        Map<String, String> result = new LinkedHashMap<>();
        parsed.forEach((name, path) -> {
            if (StringUtils.hasText(path)) {
                result.put(name, path.trim());
            } else {
                result.put(name, defaultPrefix.endsWith(".") ? defaultPrefix + name : defaultPrefix + "." + name);
            }
        });
        return result;
    }

    /**
     * 按任务执行时的结构化输入绑定重建输入表。
     * <p>
     * 这里与任务执行引擎保持一致：对 rt.* 输入按 KEY 正序读取，
     * 再按行序对齐生成 KEY=0..n-1 的导出表，避免套用时序导出语义。
     * </p>
     *
     * @param task 任务实体
     * @param rule 关联规则
     * @param snapshot 任务执行快照
     * @return 结构化输入表
     */
    private StructuredTableData loadStructuredInputTable(TaskEntity task,
                                                         AssociationRuleEntity rule,
                                                         TaskExecutionSnapshot snapshot) {
        Map<String, String> inputBindings = resolveInputBindings(task, rule, snapshot);
        List<TaskExecutionBinding> orderedBindings = resolveStructuredInputBindings(snapshot, inputBindings);
        if (orderedBindings.isEmpty()) {
            return new StructuredTableData(List.of("KEY"), List.of());
        }

        Map<String, StructuredRtExportTableRequest> tableRequests = new LinkedHashMap<>();
        for (TaskExecutionBinding binding : orderedBindings) {
            StructuredRtExportColumnPath columnPath = parseStructuredRtExportColumnPath(binding.getResolvedPath());
            StructuredRtExportTableRequest request = tableRequests.computeIfAbsent(
                columnPath.displayTablePath(),
                ignored -> new StructuredRtExportTableRequest(columnPath.displayTablePath(), columnPath.sqlTablePath())
            );
            request.aliasToSqlColumns().putIfAbsent(binding.getName(), columnPath.sqlColumnName());
        }

        Map<String, Map<String, List<Object>>> tableSeriesMap = new LinkedHashMap<>();
        Integer expectedRowCount = null;
        for (StructuredRtExportTableRequest request : tableRequests.values()) {
            StructuredRtExportTableSeries tableSeries = queryStructuredExportTableSeries(request);
            tableSeriesMap.put(request.displayTablePath(), tableSeries.columnValues());
            if (expectedRowCount == null) {
                expectedRowCount = tableSeries.rowCount();
            } else if (!expectedRowCount.equals(tableSeries.rowCount())) {
                throw BizException.badRequest("结构化输入任务导出失败：输入表记录数不一致，无法按执行顺序对齐");
            }
        }

        int rowCount = expectedRowCount == null ? 0 : expectedRowCount;
        List<String> columns = new ArrayList<>();
        columns.add("KEY");
        for (TaskExecutionBinding binding : orderedBindings) {
            columns.add(binding.getName());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("KEY", (long) rowIndex);
            for (TaskExecutionBinding binding : orderedBindings) {
                StructuredRtExportColumnPath columnPath = parseStructuredRtExportColumnPath(binding.getResolvedPath());
                Map<String, List<Object>> tableValues = tableSeriesMap.get(columnPath.displayTablePath());
                List<Object> values = tableValues == null ? null : tableValues.get(binding.getName());
                Object value = values != null && rowIndex < values.size() ? values.get(rowIndex) : null;
                row.put(binding.getName(), normalizeStructuredDisplayValue(value));
            }
            rows.add(row);
        }
        return new StructuredTableData(columns, rows);
    }

    /**
     * 解析结构化输入绑定顺序。
     *
     * @param snapshot 任务执行快照
     * @param inputBindings 输入绑定映射
     * @return 有序绑定列表
     */
    private List<TaskExecutionBinding> resolveStructuredInputBindings(TaskExecutionSnapshot snapshot,
                                                                      Map<String, String> inputBindings) {
        List<TaskExecutionBinding> result = new ArrayList<>();
        if (snapshot != null && snapshot.getInputs() != null && !snapshot.getInputs().isEmpty()) {
            for (TaskExecutionBinding binding : snapshot.getInputs()) {
                if (binding == null || !"RT".equalsIgnoreCase(binding.getPathKind())) {
                    continue;
                }
                if (!StringUtils.hasText(binding.getName()) || !StringUtils.hasText(binding.getResolvedPath())) {
                    continue;
                }
                result.add(binding);
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        if (inputBindings == null || inputBindings.isEmpty()) {
            return List.of();
        }
        inputBindings.forEach((name, path) -> {
            if (!StringUtils.hasText(name) || !StringUtils.hasText(path)) {
                return;
            }
            TaskExecutionBinding binding = new TaskExecutionBinding();
            binding.setName(name);
            binding.setResolvedPath(path);
            binding.setPathKind("RT");
            result.add(binding);
        });
        return result;
    }

    /**
     * 构建结构化表导出字节。
     *
     * @param tableData 表格数据
     * @param format 导出格式
     * @return 导出字节
     */
    private byte[] buildStructuredTableBytes(StructuredTableData tableData, String format) {
        if ("JSON".equalsIgnoreCase(format)) {
            return buildStructuredTableJson(tableData);
        }
        return buildStructuredTableCsv(tableData);
    }

    /**
     * 构建结构化表 CSV。
     *
     * @param tableData 表格数据
     * @return CSV 字节
     */
    private byte[] buildStructuredTableCsv(StructuredTableData tableData) {
        List<String> columns = tableData == null || tableData.columns() == null || tableData.columns().isEmpty()
            ? List.of("KEY")
            : tableData.columns();
        List<Map<String, Object>> rows = tableData == null || tableData.rows() == null
            ? List.of()
            : tableData.rows();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            writer.write(columns.stream().map(CsvUtils::toCsvValue).reduce((a, b) -> a + "," + b).orElse(""));
            writer.newLine();
            for (Map<String, Object> row : rows) {
                List<String> values = new ArrayList<>();
                for (String column : columns) {
                    Object value = row == null ? null : row.get(column);
                    values.add(normalizeValue(value));
                }
                writer.write(values.stream().map(CsvUtils::toCsvValue).reduce((a, b) -> a + "," + b).orElse(""));
                writer.newLine();
            }
            writer.flush();
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw BizException.internal("导出结构化表失败: " + ex.getMessage());
        }
    }

    /**
     * 构建结构化表 JSON。
     *
     * @param tableData 表格数据
     * @return JSON 字节
     */
    private byte[] buildStructuredTableJson(StructuredTableData tableData) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("columns", tableData == null ? List.of("KEY") : tableData.columns());
        root.put("rows", tableData == null ? List.of() : tableData.rows());
        return writeJsonBytes(root);
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
     * 构建结构化任务报告中的表格预览块。
     *
     * @param inputTable 输入表
     * @param outputTable 输出表
     * @return 表格块列表
     */
    private List<ReportPdfBuilder.TableBlock> buildStructuredReportTables(StructuredTableData inputTable,
                                                                          StructuredTableData outputTable) {
        List<ReportPdfBuilder.TableBlock> blocks = new ArrayList<>();
        if (inputTable != null && inputTable.rows() != null && !inputTable.rows().isEmpty()) {
            blocks.add(new ReportPdfBuilder.TableBlock(
                "Structured Input Preview",
                limitColumns(inputTable.columns(), 6),
                limitTableRows(inputTable, 20)
            ));
        }
        if (outputTable != null && outputTable.rows() != null && !outputTable.rows().isEmpty()) {
            blocks.add(new ReportPdfBuilder.TableBlock(
                "Structured Result Preview",
                limitColumns(outputTable.columns(), 6),
                limitTableRows(outputTable, 20)
            ));
        }
        return blocks;
    }

    /**
     * 裁剪表格列数，避免 PDF 一页过宽。
     *
     * @param columns 原始列
     * @param maxColumns 最大列数
     * @return 裁剪后的列
     */
    private List<String> limitColumns(List<String> columns, int maxColumns) {
        if (columns == null || columns.isEmpty()) {
            return List.of("KEY");
        }
        if (columns.size() <= maxColumns) {
            return new ArrayList<>(columns);
        }
        return new ArrayList<>(columns.subList(0, maxColumns));
    }

    /**
     * 裁剪表格行数并限制列集合。
     *
     * @param tableData 表格数据
     * @param maxRows 最大行数
     * @return 预览行
     */
    private List<Map<String, Object>> limitTableRows(StructuredTableData tableData, int maxRows) {
        if (tableData == null || tableData.rows() == null || tableData.rows().isEmpty()) {
            return List.of();
        }
        List<String> columns = limitColumns(tableData.columns(), 6);
        int size = Math.min(tableData.rows().size(), maxRows);
        List<Map<String, Object>> previewRows = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            Map<String, Object> source = tableData.rows().get(index);
            Map<String, Object> row = new LinkedHashMap<>();
            for (String column : columns) {
                row.put(column, source == null ? null : source.get(column));
            }
            previewRows.add(row);
        }
        return previewRows;
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
                                      String analysisMode,
                                      Map<String, byte[]> entries) {
        StringBuilder builder = new StringBuilder();
        builder.append("导出时间: ").append(TimeParser.formatMillis(System.currentTimeMillis())).append("\n");
        builder.append("任务ID: ").append(task.getId()).append("\n");
        builder.append("规则名称: ").append(rule.getName()).append("\n");
        builder.append("分析模式: ").append(analysisMode).append("\n");
        if (asset != null) {
            builder.append("模型文件: ").append(asset.getFileName()).append("\n");
            builder.append("模型版本: ").append(asset.getVersion()).append("\n");
        }
        builder.append("时间范围: ")
            .append(task.getRangeStart() == null ? "-" : task.getRangeStart())
            .append(" ~ ")
            .append(task.getRangeEnd() == null ? "-" : task.getRangeEnd())
            .append("\n\n");
        if ("STRUCTURED".equalsIgnoreCase(analysisMode)) {
            builder.append("说明: 结构化输入任务按行序对齐导出输入表与结果表，KEY 为导出时生成的 0 开始序号。\n\n");
        }
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
     * 计算结构化结果表的数值统计信息。
     *
     * @param tableData 结果表
     * @return 统计结果
     */
    private Map<String, Stats> calculateStructuredStats(StructuredTableData tableData) {
        if (tableData == null || tableData.columns() == null || tableData.columns().isEmpty()
            || tableData.rows() == null || tableData.rows().isEmpty()) {
            return Map.of();
        }
        Map<String, Stats> result = new LinkedHashMap<>();
        for (String column : tableData.columns()) {
            if (!StringUtils.hasText(column) || "KEY".equalsIgnoreCase(column)) {
                continue;
            }
            double sum = 0d;
            long count = 0L;
            Double min = null;
            Double max = null;
            for (Map<String, Object> row : tableData.rows()) {
                Object value = row == null ? null : row.get(column);
                Double numeric = toDouble(value);
                if (numeric == null) {
                    continue;
                }
                count++;
                sum += numeric;
                min = min == null ? numeric : Math.min(min, numeric);
                max = max == null ? numeric : Math.max(max, numeric);
            }
            if (count > 0) {
                result.put(column, new Stats(count, min, max, sum / count));
            }
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
    private ReportPdfBuilder.ReportContent buildTimeSeriesReportContent(TaskEntity task,
                                                                       AssociationRuleEntity rule,
                                                                       ModelAssetEntity asset,
                                                                       Map<String, String> outputPaths,
                                                                       Map<String, Stats> stats,
                                                                       SessionQueryDataSet dataSet,
                                                                       TaskReportRequest request) {
        ReportPdfBuilder.ReportContent content = new ReportPdfBuilder.ReportContent();
        content.setTitle("Experiment Report");
        content.setAnalysisMode("TIME_SERIES");
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
     * 构建结构化任务报告内容。
     *
     * @param task 任务实体
     * @param rule 关联规则
     * @param asset 模型资产
     * @param outputPaths 输出路径
     * @param stats 统计信息
     * @param inputTable 输入预览表
     * @param outputTable 输出预览表
     * @param request 报告请求
     * @return 报告内容
     */
    private ReportPdfBuilder.ReportContent buildStructuredReportContent(TaskEntity task,
                                                                       AssociationRuleEntity rule,
                                                                       ModelAssetEntity asset,
                                                                       Map<String, String> outputPaths,
                                                                       Map<String, Stats> stats,
                                                                       StructuredTableData inputTable,
                                                                       StructuredTableData outputTable,
                                                                       TaskReportRequest request) {
        ReportPdfBuilder.ReportContent content = new ReportPdfBuilder.ReportContent();
        content.setTitle("Experiment Report");
        content.setAnalysisMode("STRUCTURED");
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
        content.setRangeStart("-");
        content.setRangeEnd("-");
        content.setOutputPaths(outputPaths == null ? List.of() : new ArrayList<>(outputPaths.values()));
        content.setMetrics(buildReportMetrics(stats));
        content.setTableBlocks(buildStructuredReportTables(inputTable, outputTable));
        content.setIncludeStats(request == null || request.isIncludeStats());
        // 结构化输入任务不生成时序折线图，前端若勾选图表选项也在这里统一忽略。
        content.setIncludeCharts(false);
        content.setChartData(null);
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
     * 查询结构化输入表的完整列数据，按 KEY 正序返回。
     *
     * @param request 表查询请求
     * @return 列数据与行数
     */
    private StructuredRtExportTableSeries queryStructuredExportTableSeries(StructuredRtExportTableRequest request) {
        String selectedColumns = request.aliasToSqlColumns().entrySet().stream()
            .map(entry -> entry.getValue() + " AS " + IginxStructuredUtils.quoteIdentifier(entry.getKey()))
            .reduce((left, right) -> left + ", " + right)
            .orElseThrow(() -> BizException.badRequest("结构化输入列不能为空"));
        String sql = "SELECT " + selectedColumns
            + " FROM " + request.sqlTablePath()
            + " WHERE KEY <> " + IginxStructuredUtils.DUMMY_KEY
            + " ORDER BY KEY ASC";
        QueryDataSet dataSet = structuredQueryHelper.executeQuery(sql, 1000);
        try {
            List<String> headers = IginxStructuredUtils.normalizeStructuredHeaders(dataSet.getColumnList());
            Map<String, List<Object>> columnValues = new LinkedHashMap<>();
            for (String header : headers) {
                if (StringUtils.hasText(header)) {
                    columnValues.putIfAbsent(header, new ArrayList<>());
                }
            }
            Object[] row;
            int rowCount = 0;
            while ((row = nextStructuredRow(dataSet)) != null) {
                rowCount++;
                for (int index = 0; index < headers.size(); index++) {
                    String header = headers.get(index);
                    if (!StringUtils.hasText(header)) {
                        continue;
                    }
                    Object raw = index < row.length ? row[index] : null;
                    columnValues.computeIfAbsent(header, ignored -> new ArrayList<>())
                        .add(normalizeStructuredDisplayValue(raw));
                }
            }
            if (rowCount == 0) {
                throw BizException.badRequest("未查询到结构化输入数据: " + request.displayTablePath());
            }
            return new StructuredRtExportTableSeries(columnValues, rowCount);
        } finally {
            closeStructuredQueryQuietly(dataSet);
        }
    }

    /**
     * 解析结构化列路径。
     *
     * @param path 原始路径
     * @return 解析结果
     */
    private StructuredRtExportColumnPath parseStructuredRtExportColumnPath(String path) {
        List<String> segments = IginxStructuredUtils.splitPathSegments(path);
        if (!segments.isEmpty() && "root".equalsIgnoreCase(segments.get(0))) {
            segments = new ArrayList<>(segments.subList(1, segments.size()));
        }
        if (segments.size() < 3 || !"rt".equalsIgnoreCase(segments.get(0))) {
            throw BizException.badRequest("结构化输入路径格式错误: " + path);
        }
        String columnName = segments.get(segments.size() - 1);
        List<String> tableSegments = new ArrayList<>(segments.subList(0, segments.size() - 1));
        String displayTablePath = String.join(".", tableSegments);
        String sqlTablePath = tableSegments.stream()
            .map(IginxStructuredUtils::quoteIdentifier)
            .reduce((left, right) -> left + "." + right)
            .orElseThrow(() -> BizException.badRequest("结构化输入路径格式错误: " + path));
        return new StructuredRtExportColumnPath(
            displayTablePath,
            sqlTablePath,
            columnName,
            IginxStructuredUtils.quoteIdentifier(columnName)
        );
    }

    /**
     * 安全读取结构化查询结果下一行。
     *
     * @param dataSet 查询结果集
     * @return 下一行或 null
     */
    private Object[] nextStructuredRow(QueryDataSet dataSet) {
        if (dataSet == null) {
            return null;
        }
        try {
            return dataSet.nextRow();
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 安静关闭结构化查询结果集。
     *
     * @param dataSet 查询结果集
     */
    private void closeStructuredQueryQuietly(QueryDataSet dataSet) {
        if (dataSet == null) {
            return;
        }
        try {
            dataSet.close();
        } catch (Exception ignored) {
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
     * 分析查询选项。
     */
    private record AnalysisQueryOptions(boolean relative,
                                        boolean downsample,
                                        String aggregator,
                                        Long precisionMs,
                                        int pageNum,
                                        int pageSize) {
    }

    /**
     * 结构化表导出模型。
     *
     * @param columns 列集合
     * @param rows 行集合
     */
    private record StructuredTableData(List<String> columns, List<Map<String, Object>> rows) {
    }

    /**
     * 结构化结果查询上下文。
     */
    private record StructuredResultContext(LinkedHashMap<String, String> outputPaths,
                                           List<String> columns) {
    }

    /**
     * 结构化列路径解析结果。
     *
     * @param displayTablePath 展示表路径
     * @param sqlTablePath SQL 表路径
     * @param columnName 列名
     * @param sqlColumnName SQL 列名
     */
    private record StructuredRtExportColumnPath(String displayTablePath,
                                                String sqlTablePath,
                                                String columnName,
                                                String sqlColumnName) {
    }

    /**
     * 结构化表查询请求。
     *
     * @param displayTablePath 展示表路径
     * @param sqlTablePath SQL 表路径
     */
    private record StructuredRtExportTableRequest(String displayTablePath,
                                                  String sqlTablePath,
                                                  LinkedHashMap<String, String> aliasToSqlColumns) {
        private StructuredRtExportTableRequest(String displayTablePath, String sqlTablePath) {
            this(displayTablePath, sqlTablePath, new LinkedHashMap<>());
        }
    }

    /**
     * 结构化表列数据查询结果。
     *
     * @param columnValues 列数据
     * @param rowCount 行数
     */
    private record StructuredRtExportTableSeries(Map<String, List<Object>> columnValues, int rowCount) {
    }

    /**
     * 本地降采样桶。
     */
    private static final class LocalDownsampleBucket {
        private final long bucketIndex;
        private final long timestampNs;
        private long nonNullCount;
        private long numericCount;
        private double sum;
        private Double min;
        private Double max;
        private Double first;
        private Double last;

        private LocalDownsampleBucket(long bucketIndex, long timestampNs) {
            this.bucketIndex = bucketIndex;
            this.timestampNs = timestampNs;
        }

        private long bucketIndex() {
            return bucketIndex;
        }

        private long timestampNs() {
            return timestampNs;
        }

        private void add(Object rawValue, Double numericValue) {
            if (rawValue != null) {
                nonNullCount++;
            }
            if (numericValue == null) {
                return;
            }
            if (first == null) {
                first = numericValue;
            }
            last = numericValue;
            sum += numericValue;
            numericCount++;
            min = min == null ? numericValue : Math.min(min, numericValue);
            max = max == null ? numericValue : Math.max(max, numericValue);
        }

        private Double aggregate(String aggregator) {
            return switch (aggregator) {
                case "MAX" -> max;
                case "MIN" -> min;
                case "SUM" -> numericCount > 0 ? sum : null;
                case "COUNT" -> (double) nonNullCount;
                case "FIRST" -> first;
                case "LAST" -> last;
                default -> numericCount > 0 ? sum / numericCount : null;
            };
        }
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

