package com.xmu.iginx.assoc.modules.task.service.impl;

import cn.edu.tsinghua.iginx.session.QueryDataSet;
import cn.edu.tsinghua.iginx.session.SessionExecuteSqlResult;
import cn.edu.tsinghua.iginx.session.SessionQueryDataSet;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredQueryHelper;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredUtils;
import com.xmu.iginx.assoc.modules.data.util.TimeParser;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionBinding;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionOutcome;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionPlan;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务模型执行引擎。
 * <p>
 * 负责串联以下步骤：
 * 1. 从 IGinX 读取已绑定的 ts.* / rt.* 输入；
 * 2. 根据模型类型自动选择 Python / MATLAB / C++ 执行器；
 * 3. 将函数实际返回结果与规则定义输出做严格对齐；
 * 4. 通过 IGinX SQL 将结果写回默认或自定义路径。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskModelExecutionEngine {

    private static final int SQL_INSERT_BATCH_SIZE = 200;

    private final IginxStorageWrapper iginxStorageWrapper;
    private final IginxStructuredQueryHelper structuredQueryHelper;
    private final List<TaskModelExecutor> taskModelExecutors;

    /**
     * 执行任务计划。
     *
     * @param plan 任务执行计划
     * @param modelBytes 模型文件字节
     * @return 执行结果摘要
     * @throws Exception 执行异常
     */
    public TaskExecutionOutcome execute(TaskExecutionPlan plan, byte[] modelBytes) throws Exception {
        if (plan == null || plan.getSnapshot() == null) {
            throw BizException.internal("任务执行计划不存在");
        }
        TaskExecutionSnapshot snapshot = plan.getSnapshot();
        LoadedInputs loadedInputs = loadInputs(snapshot);
        TaskModelExecutor executor = resolveExecutor(plan.getModelType());

        LinkedHashMap<String, Object> functionArguments = new LinkedHashMap<>();
        for (TaskExecutionBinding binding : safeBindings(snapshot.getInputs())) {
            functionArguments.put(binding.getName(), loadedInputs.arguments().get(binding.getName()));
        }

        TaskModelExecutor.ExecutionResult executionResult = executor.execute(plan, functionArguments, modelBytes);
        LinkedHashMap<String, Object> outputs = normalizeOutputs(executionResult.rawResult(), safeBindings(snapshot.getOutputs()));
        OutputWriteSummary writeSummary = writeOutputs(snapshot, loadedInputs, outputs);

        TaskExecutionOutcome outcome = new TaskExecutionOutcome();
        outcome.setWrittenOutputCount(writeSummary.writtenOutputCount());
        outcome.setOutputValueCounts(writeSummary.outputValueCounts());
        outcome.setExecLog(buildExecLog(plan, snapshot, loadedInputs, outputs, executionResult.runtimeLog()));
        return outcome;
    }

    /**
     * 解析并加载任务输入。
     */
    private LoadedInputs loadInputs(TaskExecutionSnapshot snapshot) {
        List<TaskExecutionBinding> inputBindings = safeBindings(snapshot.getInputs());
        List<TaskExecutionBinding> tsBindings = inputBindings.stream()
            .filter(item -> "TS".equalsIgnoreCase(item.getPathKind()))
            .toList();
        List<TaskExecutionBinding> rtBindings = inputBindings.stream()
            .filter(item -> "RT".equalsIgnoreCase(item.getPathKind()))
            .toList();

        LinkedHashMap<String, Object> arguments = new LinkedHashMap<>();
        long[] timelineKeys = new long[0];
        int structuredRowCount = 0;
        boolean structuredOnlyInput = tsBindings.isEmpty() && !rtBindings.isEmpty();

        if (!tsBindings.isEmpty()) {
            if (snapshot.getRangeStart() == null || snapshot.getRangeEnd() == null) {
                throw BizException.badRequest("任务输入包含 ts 路径，必须选择时间区间");
            }
            long startNs = toNano(snapshot.getRangeStart());
            long endNs = toNano(snapshot.getRangeEnd());
            SessionQueryDataSet dataSet = queryDataWithRootFallback(tsBindings.stream()
                .map(TaskExecutionBinding::getResolvedPath)
                .collect(Collectors.toCollection(ArrayList::new)), startNs, endNs);
            if (isDataSetEmpty(dataSet)) {
                throw BizException.badRequest("任务输入数据为空，无法执行模型函数");
            }
            timelineKeys = dataSet.getKeys() == null ? new long[0] : dataSet.getKeys();
            Map<String, Integer> pathIndex = buildPathIndex(dataSet.getPaths());
            List<List<Object>> rows = dataSet.getValues() == null ? List.of() : dataSet.getValues();
            for (TaskExecutionBinding binding : tsBindings) {
                Integer index = resolvePathIndex(pathIndex, binding.getResolvedPath());
                if (index == null) {
                    throw BizException.badRequest("未查询到输入路径数据: " + binding.getResolvedPath());
                }
                List<Object> seriesValues = new ArrayList<>();
                for (List<Object> row : rows) {
                    Object raw = index < row.size() ? row.get(index) : null;
                    seriesValues.add(convertScalarValue(raw, binding.getType()));
                }
                arguments.put(binding.getName(), seriesValues);
            }
        }

        if (!rtBindings.isEmpty()) {
            if (structuredOnlyInput) {
                StructuredRtInputs structuredInputs = loadStructuredRtSeriesInputs(rtBindings);
                arguments.putAll(structuredInputs.arguments());
                structuredRowCount = structuredInputs.rowCount();
            } else {
                arguments.putAll(loadStructuredRtLatestRowInputs(rtBindings));
            }
        }

        return new LoadedInputs(arguments, timelineKeys, structuredRowCount, structuredOnlyInput);
    }

    /**
     * 加载 rt.* 结构化输入。
     * <p>
     * 约定：
     * 1. `rt.xxx.yyy.temperature` 表示结构化表 `rt.xxx.yyy` 的列 `temperature`；
     * 2. 同一张表上的多个输入参数，统一读取该表“最新一行”的列值，保证同源一致；
     * 3. 若表中没有任何记录，或目标列不存在，则禁止任务继续执行。
     * </p>
     */
    private LinkedHashMap<String, Object> loadStructuredRtLatestRowInputs(List<TaskExecutionBinding> rtBindings) {
        Map<String, StructuredRtTableRequest> tableRequests = new LinkedHashMap<>();
        for (TaskExecutionBinding binding : rtBindings) {
            StructuredRtColumnPath columnPath = parseStructuredRtColumnPath(binding.getResolvedPath());
            StructuredRtTableRequest request = tableRequests.computeIfAbsent(
                columnPath.displayTablePath(),
                ignored -> new StructuredRtTableRequest(columnPath.displayTablePath(), columnPath.sqlTablePath())
            );
            request.columns().putIfAbsent(columnPath.columnName(), columnPath.sqlColumnName());
        }

        Map<String, Map<String, Object>> latestRowValues = new LinkedHashMap<>();
        for (StructuredRtTableRequest request : tableRequests.values()) {
            latestRowValues.put(request.displayTablePath(), queryLatestStructuredRow(request));
        }

        LinkedHashMap<String, Object> arguments = new LinkedHashMap<>();
        for (TaskExecutionBinding binding : rtBindings) {
            StructuredRtColumnPath columnPath = parseStructuredRtColumnPath(binding.getResolvedPath());
            Map<String, Object> rowValues = latestRowValues.get(columnPath.displayTablePath());
            if (rowValues == null || !rowValues.containsKey(columnPath.columnName())) {
                throw BizException.badRequest("未查询到输入路径数据: " + binding.getResolvedPath());
            }
            arguments.put(binding.getName(), convertScalarValue(rowValues.get(columnPath.columnName()), binding.getType()));
        }
        return arguments;
    }

    /**
     * 按整表列读取纯 rt.* 输入。
     * <p>
     * 当任务输入全部来自结构化表时，需要将每个绑定列的完整数据作为序列传入模型，
     * 以便 Python / MATLAB / C++ 执行器逐行调用模型函数，并生成完整结果序列。
     * </p>
     */
    private StructuredRtInputs loadStructuredRtSeriesInputs(List<TaskExecutionBinding> rtBindings) {
        Map<String, StructuredRtTableRequest> tableRequests = new LinkedHashMap<>();
        for (TaskExecutionBinding binding : rtBindings) {
            StructuredRtColumnPath columnPath = parseStructuredRtColumnPath(binding.getResolvedPath());
            StructuredRtTableRequest request = tableRequests.computeIfAbsent(
                columnPath.displayTablePath(),
                ignored -> new StructuredRtTableRequest(columnPath.displayTablePath(), columnPath.sqlTablePath())
            );
            request.columns().putIfAbsent(columnPath.columnName(), columnPath.sqlColumnName());
        }

        Map<String, Map<String, List<Object>>> tableSeriesMap = new LinkedHashMap<>();
        Integer expectedRowCount = null;
        for (StructuredRtTableRequest request : tableRequests.values()) {
            StructuredRtTableSeries tableSeries = queryStructuredTableSeries(request);
            tableSeriesMap.put(request.displayTablePath(), tableSeries.columnValues());
            if (expectedRowCount == null) {
                expectedRowCount = tableSeries.rowCount();
            } else if (!expectedRowCount.equals(tableSeries.rowCount())) {
                throw BizException.badRequest("纯 rt 输入任务要求各结构化表记录数一致，当前不一致: "
                    + request.displayTablePath());
            }
        }

        LinkedHashMap<String, Object> arguments = new LinkedHashMap<>();
        int rowCount = expectedRowCount == null ? 0 : expectedRowCount;
        for (TaskExecutionBinding binding : rtBindings) {
            StructuredRtColumnPath columnPath = parseStructuredRtColumnPath(binding.getResolvedPath());
            Map<String, List<Object>> columnSeries = tableSeriesMap.get(columnPath.displayTablePath());
            List<Object> values = columnSeries == null ? null : columnSeries.get(columnPath.columnName());
            if (values == null) {
                throw BizException.badRequest("未查询到输入路径数据: " + binding.getResolvedPath());
            }
            List<Object> normalized = new ArrayList<>(values.size());
            for (Object value : values) {
                normalized.add(convertScalarValue(value, binding.getType()));
            }
            arguments.put(binding.getName(), normalized);
        }
        if (rowCount <= 0) {
            throw BizException.badRequest("未查询到 rt 输入路径数据");
        }
        return new StructuredRtInputs(arguments, rowCount);
    }

    /**
     * 查询结构化表最新一行的目标列值。
     */
    private Map<String, Object> queryLatestStructuredRow(StructuredRtTableRequest request) {
        String selectedColumns = request.columns().entrySet().stream()
            .map(entry -> entry.getValue() + " AS " + IginxStructuredUtils.quoteIdentifier(entry.getKey()))
            .collect(Collectors.joining(", "));
        String sql = "SELECT " + selectedColumns
            + " FROM " + request.sqlTablePath()
            + " ORDER BY KEY DESC LIMIT 1";
        QueryDataSet dataSet = structuredQueryHelper.executeQuery(sql, 1);
        try {
            Object[] row = nextStructuredRow(dataSet);
            if (row == null) {
                throw BizException.badRequest("未查询到 rt 输入路径的最新记录: " + request.displayTablePath());
            }
            List<String> headers = IginxStructuredUtils.normalizeStructuredHeaders(dataSet.getColumnList());
            Map<String, Object> record = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);
                if (!StringUtils.hasText(header)) {
                    continue;
                }
                Object raw = i < row.length ? row[i] : null;
                record.put(header, normalizeStructuredValue(raw));
            }
            return record;
        } finally {
            closeStructuredQueryQuietly(dataSet);
        }
    }

    /**
     * 查询结构化表的完整输入列数据，按 KEY 正序返回。
     */
    private StructuredRtTableSeries queryStructuredTableSeries(StructuredRtTableRequest request) {
        String selectedColumns = request.columns().entrySet().stream()
            .map(entry -> entry.getValue() + " AS " + IginxStructuredUtils.quoteIdentifier(entry.getKey()))
            .collect(Collectors.joining(", "));
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
                        .add(normalizeStructuredValue(raw));
                }
            }
            if (rowCount == 0) {
                throw BizException.badRequest("未查询到 rt 输入路径数据: " + request.displayTablePath());
            }
            for (String requiredColumn : request.columns().keySet()) {
                if (!columnValues.containsKey(requiredColumn)) {
                    throw BizException.badRequest("未查询到输入路径数据: " + request.displayTablePath() + "." + requiredColumn);
                }
            }
            return new StructuredRtTableSeries(columnValues, rowCount);
        } finally {
            closeStructuredQueryQuietly(dataSet);
        }
    }

    /**
     * 将 rt 列路径解析为“表路径 + 列名”。
     */
    private StructuredRtColumnPath parseStructuredRtColumnPath(String path) {
        List<String> segments = IginxStructuredUtils.splitPathSegments(path);
        if (!segments.isEmpty() && "root".equalsIgnoreCase(segments.get(0))) {
            segments = new ArrayList<>(segments.subList(1, segments.size()));
        }
        if (segments.size() < 3 || !"rt".equalsIgnoreCase(segments.get(0))) {
            throw BizException.badRequest("rt 输入路径格式错误，应为 rt.xxx.column: " + path);
        }
        String columnName = segments.get(segments.size() - 1);
        if (!StringUtils.hasText(columnName)) {
            throw BizException.badRequest("rt 输入路径缺少列名: " + path);
        }
        List<String> tableSegments = segments.subList(0, segments.size() - 1);
        String displayTablePath = String.join(".", tableSegments);
        String sqlTablePath = tableSegments.stream()
            .map(IginxStructuredUtils::quoteIdentifier)
            .collect(Collectors.joining("."));
        return new StructuredRtColumnPath(
            displayTablePath,
            sqlTablePath,
            columnName,
            IginxStructuredUtils.quoteIdentifier(columnName)
        );
    }

    /**
     * 将函数返回结果与规则定义输出严格对齐。
     */
    private LinkedHashMap<String, Object> normalizeOutputs(Object rawResult, List<TaskExecutionBinding> outputBindings) {
        if (outputBindings.isEmpty()) {
            throw BizException.badRequest("任务未定义输出参数");
        }

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (rawResult instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                normalized.put(entry.getKey().toString(), entry.getValue());
            }
            ensureExactOutputNames(outputBindings, normalized.keySet());
            for (TaskExecutionBinding binding : outputBindings) {
                result.put(binding.getName(), normalized.get(binding.getName()));
            }
            return result;
        }

        List<Object> outputValues = unwrapSequence(rawResult);
        if (outputBindings.size() == 1) {
            result.put(outputBindings.get(0).getName(), rawResult);
            return result;
        }
        if (outputValues.size() != outputBindings.size()) {
            throw BizException.badRequest("模型实际返回输出数量与规则定义不一致");
        }
        for (int i = 0; i < outputBindings.size(); i++) {
            result.put(outputBindings.get(i).getName(), outputValues.get(i));
        }
        return result;
    }

    /**
     * 写回任务输出。
     */
    private OutputWriteSummary writeOutputs(TaskExecutionSnapshot snapshot,
                                            LoadedInputs loadedInputs,
                                            LinkedHashMap<String, Object> outputs) {
        int written = 0;
        Map<String, Integer> outputValueCounts = new LinkedHashMap<>();
        long[] timelineKeys = loadedInputs.timelineKeys();
        boolean hasTimeline = timelineKeys != null && timelineKeys.length > 0;
        boolean structuredOnlyInput = loadedInputs.structuredOnlyInput();
        long singleKey = hasTimeline
            ? timelineKeys[timelineKeys.length - 1]
            : (structuredOnlyInput ? 0L : TimeParser.toNano(System.currentTimeMillis()));
        long[] sequenceKeys = hasTimeline
            ? Arrays.copyOf(timelineKeys, timelineKeys.length)
            : (loadedInputs.structuredRowCount() > 0 ? buildStructuredSequenceKeys(loadedInputs.structuredRowCount()) : new long[0]);

        for (TaskExecutionBinding binding : safeBindings(snapshot.getOutputs())) {
            Object rawValue = outputs.get(binding.getName());
            OutputWriteData writeData = resolveOutputWriteData(rawValue, binding.getType(), sequenceKeys, singleKey);
            executeInsertSql(binding.getResolvedPath(), writeData.keys(), writeData.values());
            written++;
            outputValueCounts.put(binding.getName(), writeData.keys().length);
        }
        return new OutputWriteSummary(written, outputValueCounts);
    }

    /**
     * 解析单个输出的写入数据。
     */
    private OutputWriteData resolveOutputWriteData(Object rawValue,
                                                   String expectedType,
                                                   long[] sequenceKeys,
                                                   long singleKey) {
        List<Object> sequence = unwrapSequence(rawValue);
        boolean hasSequenceKeys = sequenceKeys != null && sequenceKeys.length > 0;
        if (hasSequenceKeys && sequence.size() == sequenceKeys.length && isScalarSeries(sequence)) {
            List<Object> normalized = sequence.stream()
                .map(item -> convertOutputPoint(item, expectedType))
                .collect(Collectors.toCollection(ArrayList::new));
            return new OutputWriteData(Arrays.copyOf(sequenceKeys, sequenceKeys.length), normalized);
        }
        if (!sequence.isEmpty() && sequence.size() != 1 && !looksLikeSingleComplexValue(rawValue, expectedType)) {
            throw BizException.badRequest("模型输出长度与任务输入序列不一致，无法写回 IGinX");
        }
        Object pointValue;
        if (looksLikeSingleComplexValue(rawValue, expectedType)) {
            pointValue = convertOutputPoint(rawValue, expectedType);
        } else if (!sequence.isEmpty()) {
            pointValue = convertOutputPoint(sequence.get(0), expectedType);
        } else {
            pointValue = convertOutputPoint(rawValue, expectedType);
        }
        return new OutputWriteData(new long[]{singleKey}, List.of(pointValue));
    }

    /**
     * 为结构化任务输出生成从 0 开始的连续 KEY。
     */
    private long[] buildStructuredSequenceKeys(int rowCount) {
        long[] keys = new long[rowCount];
        for (int index = 0; index < rowCount; index++) {
            keys[index] = index;
        }
        return keys;
    }

    /**
     * 执行 IGinX SQL 插入。
     * <p>
     * 按用户手册 3.2.1.1 的语法构造：
     * INSERT INTO 前缀路径 (KEY, 后缀列) VALUES (...), (...);
     * </p>
     */
    private void executeInsertSql(String path, long[] keys, List<Object> values) {
        if (!StringUtils.hasText(path)) {
            throw BizException.badRequest("输出路径不能为空");
        }
        if (keys == null || values == null || keys.length == 0 || values.isEmpty() || keys.length != values.size()) {
            throw BizException.badRequest("输出写回数据不合法");
        }
        List<String> segments = IginxStructuredUtils.splitPathSegments(path);
        if (segments.size() < 2) {
            throw BizException.badRequest("输出路径必须包含至少一级前缀: " + path);
        }
        String prefix = segments.subList(0, segments.size() - 1).stream()
            .map(IginxStructuredUtils::quoteIdentifier)
            .collect(Collectors.joining("."));
        String suffix = IginxStructuredUtils.quoteIdentifier(segments.get(segments.size() - 1));

        for (int start = 0; start < keys.length; start += SQL_INSERT_BATCH_SIZE) {
            int end = Math.min(keys.length, start + SQL_INSERT_BATCH_SIZE);
            StringBuilder sql = new StringBuilder();
            sql.append("INSERT INTO ").append(prefix)
                .append(" (KEY, ").append(suffix).append(") VALUES ");
            for (int index = start; index < end; index++) {
                if (index > start) {
                    sql.append(", ");
                }
                sql.append("(")
                    .append(keys[index])
                    .append(", ")
                    .append(IginxStructuredUtils.toSqlLiteral(values.get(index)))
                    .append(")");
            }
            sql.append(";");
            SessionExecuteSqlResult result = iginxStorageWrapper.executeSql(sql.toString());
            if (result != null && StringUtils.hasText(result.getParseErrorMsg())) {
                throw BizException.badRequest(result.getParseErrorMsg().trim());
            }
        }
    }

    /**
     * 选择模型执行器。
     */
    private TaskModelExecutor resolveExecutor(String modelType) {
        return taskModelExecutors.stream()
            .filter(item -> item.supports(modelType))
            .findFirst()
            .orElseThrow(() -> BizException.badRequest("当前任务暂不支持该模型类型: " + modelType));
    }

    /**
     * 查询原始数据，必要时补充 root 前缀兜底。
     */
    private SessionQueryDataSet queryDataWithRootFallback(List<String> paths, long startNs, long endNs) {
        try {
            return iginxStorageWrapper.executeWithSession(session -> session.queryData(paths, startNs, endNs));
        } catch (Exception ex) {
            if (!needsRootPrefix(paths)) {
                throw ex;
            }
            List<String> fallbackPaths = addRootPrefix(paths);
            return iginxStorageWrapper.executeWithSession(session -> session.queryData(fallbackPaths, startNs, endNs));
        }
    }

    /**
     * 判断返回数据集是否为空。
     */
    private boolean isDataSetEmpty(SessionQueryDataSet dataSet) {
        return dataSet == null
            || dataSet.getKeys() == null
            || dataSet.getKeys().length == 0
            || dataSet.getValues() == null
            || dataSet.getValues().isEmpty();
    }

    /**
     * 构建路径索引，兼容 root 前缀差异。
     */
    private Map<String, Integer> buildPathIndex(List<String> paths) {
        Map<String, Integer> pathIndex = new LinkedHashMap<>();
        if (paths == null) {
            return pathIndex;
        }
        for (int i = 0; i < paths.size(); i++) {
            pathIndex.putIfAbsent(normalizeMatchKey(paths.get(i)), i);
        }
        return pathIndex;
    }

    /**
     * 根据路径定位数据列索引。
     */
    private Integer resolvePathIndex(Map<String, Integer> pathIndex, String path) {
        return pathIndex.get(normalizeMatchKey(path));
    }

    /**
     * 归一化路径匹配键。
     */
    private String normalizeMatchKey(String path) {
        String text = path == null ? "" : path.trim();
        if (text.toLowerCase(Locale.ROOT).startsWith("root.")) {
            text = text.substring("root.".length());
        }
        return text;
    }

    /**
     * 判断路径列表是否需要 root 前缀兜底。
     */
    private boolean needsRootPrefix(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return false;
        }
        return paths.stream().allMatch(path -> {
            String text = path == null ? "" : path.trim().toLowerCase(Locale.ROOT);
            return !text.startsWith("root.");
        });
    }

    /**
     * 为路径补充 root 前缀。
     */
    private List<String> addRootPrefix(List<String> paths) {
        List<String> result = new ArrayList<>();
        for (String path : paths) {
            String text = path == null ? "" : path.trim();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            result.add(text.toLowerCase(Locale.ROOT).startsWith("root.") ? text : "root." + text);
        }
        return result;
    }

    /**
     * 安全读取结构化查询下一行。
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
     * 规范化结构化查询返回值，二进制列统一转 UTF-8 字符串。
     */
    private Object normalizeStructuredValue(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value;
    }

    /**
     * 将对象拆成序列。
     */
    private List<Object> unwrapSequence(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (raw instanceof Object[] array) {
            return new ArrayList<>(Arrays.asList(array));
        }
        if (raw instanceof byte[]) {
            return List.of(new String((byte[]) raw, StandardCharsets.UTF_8));
        }
        return List.of(raw);
    }

    /**
     * 判断是否为可直接按时序写回的标量序列。
     */
    private boolean isScalarSeries(List<Object> values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof Map<?, ?>) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断原值是否应视为“单个复杂对象”，而不是“多点序列”。
     */
    private boolean looksLikeSingleComplexValue(Object raw, String expectedType) {
        String type = normalizeType(expectedType);
        return "ARRAY".equals(type)
            || "OBJECT".equals(type)
            || raw instanceof Map<?, ?>;
    }

    /**
     * 输出名称集合必须与规则定义严格一致。
     */
    private void ensureExactOutputNames(List<TaskExecutionBinding> bindings, Set<String> actualNames) {
        Set<String> expected = bindings.stream()
            .map(TaskExecutionBinding::getName)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> actual = actualNames == null
            ? Set.of()
            : actualNames.stream().filter(StringUtils::hasText)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (expected.equals(actual)) {
            return;
        }
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        Set<String> extra = new LinkedHashSet<>(actual);
        extra.removeAll(expected);
        throw BizException.badRequest("模型实际返回输出与规则定义不一致，缺失: " + missing + "，多余: " + extra);
    }

    /**
     * 输入值转换。
     */
    private Object convertScalarValue(Object rawValue, String expectedType) {
        if (rawValue == null) {
            return null;
        }
        String type = normalizeType(expectedType);
        if ("ARRAY".equals(type) || "OBJECT".equals(type)) {
            return normalizeComplexValue(rawValue);
        }
        if (rawValue instanceof byte[] bytes) {
            rawValue = new String(bytes, StandardCharsets.UTF_8);
        }
        return switch (type) {
            case "FLOAT" -> toDouble(rawValue, "输入值");
            case "INT" -> toLong(rawValue, "输入值");
            case "BOOLEAN" -> toBoolean(rawValue, "输入值");
            default -> rawValue == null ? null : String.valueOf(rawValue);
        };
    }

    /**
     * 输出点值转换。
     */
    private Object convertOutputPoint(Object rawValue, String expectedType) {
        if (rawValue == null) {
            return null;
        }
        String type = normalizeType(expectedType);
        if ("ARRAY".equals(type) || "OBJECT".equals(type)) {
            if (rawValue instanceof String text) {
                return text;
            }
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(normalizeComplexValue(rawValue));
            } catch (Exception ex) {
                return String.valueOf(rawValue);
            }
        }
        if (rawValue instanceof byte[] bytes) {
            rawValue = new String(bytes, StandardCharsets.UTF_8);
        }
        return switch (type) {
            case "FLOAT" -> toDouble(rawValue, "输出值");
            case "INT" -> toLong(rawValue, "输出值");
            case "BOOLEAN" -> toBoolean(rawValue, "输出值");
            default -> String.valueOf(rawValue);
        };
    }

    /**
     * 复杂值归一化。
     */
    private Object normalizeComplexValue(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (rawValue instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), normalizeComplexValue(entry.getValue()));
            }
            return result;
        }
        if (rawValue instanceof List<?> list) {
            return list.stream().map(this::normalizeComplexValue).collect(Collectors.toCollection(ArrayList::new));
        }
        if (rawValue instanceof Object[] array) {
            return Arrays.stream(array).map(this::normalizeComplexValue).collect(Collectors.toCollection(ArrayList::new));
        }
        return rawValue;
    }

    /**
     * 数值转 Double。
     */
    private Double toDouble(Object rawValue, String fieldName) {
        if (rawValue instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(rawValue).trim());
        } catch (Exception ex) {
            throw BizException.badRequest(fieldName + "无法转换为 FLOAT: " + rawValue);
        }
    }

    /**
     * 数值转 Long。
     */
    private Long toLong(Object rawValue, String fieldName) {
        if (rawValue instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(rawValue).trim());
        } catch (Exception ex) {
            throw BizException.badRequest(fieldName + "无法转换为 INT: " + rawValue);
        }
    }

    /**
     * 数值转 Boolean。
     */
    private Boolean toBoolean(Object rawValue, String fieldName) {
        if (rawValue instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(rawValue).trim();
        if ("1".equals(text)) {
            return true;
        }
        if ("0".equals(text)) {
            return false;
        }
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return Boolean.parseBoolean(text);
        }
        throw BizException.badRequest(fieldName + "无法转换为 BOOLEAN: " + rawValue);
    }

    /**
     * 类型归一化。
     */
    private String normalizeType(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return "STRING";
        }
        String type = rawType.trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "DOUBLE", "FLOAT", "REAL", "DECIMAL", "SINGLE" -> "FLOAT";
            case "INT", "INTEGER", "LONG", "SHORT", "INT64", "INT32", "INT16", "INT8", "UINT8", "UINT16", "UINT32" -> "INT";
            case "BOOL", "BOOLEAN", "LOGICAL" -> "BOOLEAN";
            case "ARRAY" -> "ARRAY";
            case "OBJECT", "MAP", "DICT", "JSON" -> "OBJECT";
            default -> "STRING";
        };
    }

    /**
     * 纳秒时间转换。
     */
    private long toNano(LocalDateTime time) {
        long millis = time.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        return TimeParser.toNano(millis);
    }

    /**
     * 构建执行日志。
     */
    private String buildExecLog(TaskExecutionPlan plan,
                                TaskExecutionSnapshot snapshot,
                                LoadedInputs loadedInputs,
                                LinkedHashMap<String, Object> outputs,
                                String runtimeLog) {
        StringBuilder builder = new StringBuilder();
        builder.append("任务执行成功\n")
            .append("规则: ").append(plan.getRuleName()).append('\n')
            .append("模型类型: ").append(plan.getModelType()).append('\n')
            .append("模型版本: ").append(plan.getModelVersion()).append('\n')
            .append("调用函数: ").append(snapshot.getFunctionName()).append('\n')
            .append("输入参数: ").append(safeBindings(snapshot.getInputs()).stream()
                .map(item -> item.getName() + " <- " + item.getResolvedPath())
                .collect(Collectors.joining(", "))).append('\n')
            .append("输出参数: ").append(safeBindings(snapshot.getOutputs()).stream()
                .map(item -> item.getName() + " -> " + item.getResolvedPath())
                .collect(Collectors.joining(", "))).append('\n');
        if (loadedInputs.timelineKeys() != null && loadedInputs.timelineKeys().length > 0) {
            builder.append("时间点数量: ").append(loadedInputs.timelineKeys().length).append('\n');
        } else if (loadedInputs.structuredOnlyInput() && loadedInputs.structuredRowCount() > 0) {
            builder.append("结构化记录数量: ").append(loadedInputs.structuredRowCount()).append('\n');
        }
        builder.append("输出数量: ").append(outputs.size()).append('\n');
        if (StringUtils.hasText(runtimeLog)) {
            builder.append("模型运行日志:\n").append(runtimeLog.trim());
        }
        return builder.toString();
    }

    /**
     * 规避空集合判空散落。
     */
    private List<TaskExecutionBinding> safeBindings(List<TaskExecutionBinding> bindings) {
        return bindings == null ? List.of() : bindings;
    }

    /**
     * 已加载输入结果。
     */
    private record LoadedInputs(LinkedHashMap<String, Object> arguments,
                                long[] timelineKeys,
                                int structuredRowCount,
                                boolean structuredOnlyInput) {
    }

    /**
     * 输出写入数据。
     */
    private record OutputWriteData(long[] keys, List<Object> values) {
    }

    /**
     * 输出写入汇总。
     */
    private record OutputWriteSummary(int writtenOutputCount, Map<String, Integer> outputValueCounts) {
    }

    /**
     * 结构化 rt 列路径。
     */
    private record StructuredRtColumnPath(String displayTablePath,
                                          String sqlTablePath,
                                          String columnName,
                                          String sqlColumnName) {
    }

    /**
     * 结构化 rt 表查询请求。
     */
    private record StructuredRtTableRequest(String displayTablePath,
                                            String sqlTablePath,
                                            LinkedHashMap<String, String> columns) {

        private StructuredRtTableRequest(String displayTablePath, String sqlTablePath) {
            this(displayTablePath, sqlTablePath, new LinkedHashMap<>());
        }
    }

    /**
     * 纯结构化输入载入结果。
     */
    private record StructuredRtInputs(LinkedHashMap<String, Object> arguments, int rowCount) {
    }

    /**
     * 单张结构化表的列序列查询结果。
     */
    private record StructuredRtTableSeries(Map<String, List<Object>> columnValues, int rowCount) {
    }
}
