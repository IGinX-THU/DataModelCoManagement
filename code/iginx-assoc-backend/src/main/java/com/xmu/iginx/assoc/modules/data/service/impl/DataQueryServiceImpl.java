package com.xmu.iginx.assoc.modules.data.service.impl;

import cn.edu.tsinghua.iginx.session.QueryDataSet;
import cn.edu.tsinghua.iginx.session.SessionQueryDataSet;
import cn.edu.tsinghua.iginx.thrift.AggregateType;
import cn.edu.tsinghua.iginx.thrift.DataType;
import com.xmu.iginx.assoc.common.PageResult;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.dto.StructuredQueryCondition;
import com.xmu.iginx.assoc.modules.data.dto.StructuredQueryRequest;
import com.xmu.iginx.assoc.modules.data.dto.TimeSeriesQueryRequest;
import com.xmu.iginx.assoc.modules.data.service.DataQueryService;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredQueryHelper;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredUtils;
import com.xmu.iginx.assoc.modules.data.util.StructuredSqlBuilder;
import com.xmu.iginx.assoc.modules.data.util.TimeParser;
import com.xmu.iginx.assoc.modules.data.vo.StructuredQueryResultVO;
import com.xmu.iginx.assoc.modules.data.vo.StructuredSchemaColumnVO;
import com.xmu.iginx.assoc.modules.data.vo.StructuredSchemaVO;
import com.xmu.iginx.assoc.modules.data.vo.TimeSeriesQueryResultVO;
import com.xmu.iginx.assoc.modules.data.vo.TimeSeriesSeriesVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据查询服务实现，支持时序与结构化查询。
 */
@Service
@RequiredArgsConstructor
public class DataQueryServiceImpl implements DataQueryService {

    private static final String RT_PREFIX = "rt";
    private static final String TASK_PREFIX = "task";
    private static final String TASK_RESULT_SEGMENT = "result";

    private final IginxStructuredQueryHelper structuredQueryHelper;
    private final IginxStorageWrapper iginxStorageWrapper;
    private final StructuredSqlBuilder structuredSqlBuilder = new StructuredSqlBuilder();

    /**
     * 查询时序数据，支持可选的降采样聚合。
     *
     * @param request 查询请求
     * @return 时序查询结果
     */
    @Override
    public TimeSeriesQueryResultVO queryTimeSeries(TimeSeriesQueryRequest request) {
        if (request.getPaths() == null || request.getPaths().isEmpty()) {
            throw BizException.badRequest("测点路径不能为空");
        }
        if (request.getTimeRange() == null) {
            throw BizException.badRequest("时间范围不能为空");
        }

        // IGinX 0.8.0 的 queryData/downsampleQuery 会在内部对路径集合排序，
        // 这里必须使用可变 List，避免 stream().toList() 生成不可变集合导致 UnsupportedOperationException。
        List<String> resolvedPaths = request.getPaths().stream()
            .filter(path -> path != null && !path.isBlank())
            .collect(Collectors.toCollection(ArrayList::new));
        if (resolvedPaths.isEmpty()) {
            throw BizException.badRequest("测点路径不能为空");
        }

        long startNs = TimeParser.toNano(TimeParser.parseToMillis(request.getTimeRange().getStart(), null));
        long endNs = toInclusiveEndExclusiveNs(request.getTimeRange().getEnd());
        if (request.isDownsample() && request.getPrecisionMs() != null && request.getPrecisionMs() > 0) {
            AggregateType aggregateType = mapAggregateType(request.getAggregator());
            try {
                SessionQueryDataSet downsampled = queryTimeSeriesWithDownsample(
                    resolvedPaths,
                    startNs,
                    endNs,
                    aggregateType,
                    request.getPrecisionMs()
                );
                if (!isDataSetEmpty(downsampled)) {
                    if (shouldFallbackToLocalDownsample(request.getPrecisionMs(), request.getAggregator(), downsampled)) {
                        SessionQueryDataSet rawDataSet = queryTimeSeriesRaw(resolvedPaths, startNs, endNs);
                        return buildLocallyDownsampledResult(rawDataSet, startNs, request.getPrecisionMs(), request.getAggregator());
                    }
                    return buildTimeSeriesResult(downsampled, resolvedPaths);
                }
            } catch (BizException ex) {
                if (!shouldFallbackToLocalDownsample(request.getPrecisionMs(), ex)) {
                    throw ex;
                }
            }
            SessionQueryDataSet rawDataSet = queryTimeSeriesRaw(resolvedPaths, startNs, endNs);
            return buildLocallyDownsampledResult(rawDataSet, startNs, request.getPrecisionMs(), request.getAggregator());
        }

        SessionQueryDataSet dataSet = queryTimeSeriesRaw(resolvedPaths, startNs, endNs);
        return buildTimeSeriesResult(dataSet, resolvedPaths);
    }

    /**
     * 查询原始时序数据，必要时自动补 root 前缀重试。
     */
    private SessionQueryDataSet queryTimeSeriesRaw(List<String> paths, long startNs, long endNs) {
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
     * 查询服务端降采样结果，必要时自动补 root 前缀重试。
     */
    private SessionQueryDataSet queryTimeSeriesWithDownsample(List<String> paths,
                                                              long startNs,
                                                              long endNs,
                                                              AggregateType aggregateType,
                                                              long precisionMs) {
        List<String> primaryPaths = new ArrayList<>(paths);
        SessionQueryDataSet dataSet = iginxStorageWrapper.executeWithSession(session ->
            session.downsampleQuery(primaryPaths, startNs, endNs, aggregateType, TimeParser.toNano(precisionMs)));
        if (isDataSetEmpty(dataSet) && needsRootPrefix(primaryPaths)) {
            List<String> fallbackPaths = addRootPrefix(primaryPaths);
            dataSet = iginxStorageWrapper.executeWithSession(session ->
                session.downsampleQuery(fallbackPaths, startNs, endNs, aggregateType, TimeParser.toNano(precisionMs)));
        }
        return dataSet;
    }

    /**
     * 构建普通时序查询结果。
     */
    private TimeSeriesQueryResultVO buildTimeSeriesResult(SessionQueryDataSet dataSet, List<String> requestedPaths) {
        TimeSeriesQueryResultVO result = new TimeSeriesQueryResultVO();
        if (isDataSetEmpty(dataSet)) {
            result.setTimestamps(List.of());
            result.setSeries(List.of());
            return result;
        }

        List<Long> timestamps = new ArrayList<>();
        for (long key : dataSet.getKeys()) {
            timestamps.add(TimeParser.toMillis(key));
        }

        List<List<Object>> values = dataSet.getValues();
        List<String> paths = dataSet.getPaths();
        int valueColumnOffset = resolveValueColumnOffset(dataSet.getKeys(), values, paths == null ? 0 : paths.size());
        List<String> targetPaths = requestedPaths == null || requestedPaths.isEmpty() ? paths : requestedPaths;
        Map<String, Integer> normalizedIndex = buildNormalizedPathIndex(paths);
        List<TimeSeriesSeriesVO> series = new ArrayList<>();
        for (String targetPath : targetPaths) {
            int columnIndex = resolveDataPathIndex(paths, normalizedIndex, targetPath);
            if (columnIndex < 0) {
                continue;
            }
            TimeSeriesSeriesVO item = new TimeSeriesSeriesVO();
            item.setPath(targetPath);
            List<Object> columnValues = new ArrayList<>();
            for (List<Object> row : values) {
                Object value = resolveColumnValue(row, columnIndex, valueColumnOffset);
                // 二进制值统一转 UTF-8 字符串，避免前端无法直接展示。
                if (value instanceof byte[] bytes) {
                    columnValues.add(new String(bytes, StandardCharsets.UTF_8));
                } else {
                    columnValues.add(value);
                }
            }
            item.setValues(columnValues);
            series.add(item);
        }

        if (series.isEmpty() && paths != null) {
            for (int i = 0; i < paths.size(); i++) {
                TimeSeriesSeriesVO item = new TimeSeriesSeriesVO();
                item.setPath(paths.get(i));
                List<Object> columnValues = new ArrayList<>();
                for (List<Object> row : values) {
                    Object value = resolveColumnValue(row, i, valueColumnOffset);
                    if (value instanceof byte[] bytes) {
                        columnValues.add(new String(bytes, StandardCharsets.UTF_8));
                    } else {
                        columnValues.add(value);
                    }
                }
                item.setValues(columnValues);
                series.add(item);
            }
        }

        result.setTimestamps(timestamps);
        result.setSeries(series);
        return result;
    }

    /**
     * 将用户输入的结束时间转换为查询使用的“开区间上界”。
     *
     * <p>原因：IGinX 的 queryData/downsampleQuery 在结束时间上采用开区间语义，</p>
     * <p>而前端用户通常会把结束时间理解为“包含该时刻”。</p>
     * <p>这里统一加 1 毫秒，保证秒级/毫秒级导入的数据在结束时刻能够被查到。</p>
     *
     * @param endValue 用户输入的结束时间
     * @return 对应的纳秒级开区间上界
     */
    private long toInclusiveEndExclusiveNs(String endValue) {
        long endMillis = TimeParser.parseToMillis(endValue, null);
        if (endMillis >= Long.MAX_VALUE - 1) {
            return TimeParser.toNano(endMillis);
        }
        return TimeParser.toNano(endMillis + 1);
    }

    /**
     * 构建本地降采样结果，保证降采样功能在 IGinX 不兼容时仍可正确返回。
     */
    private TimeSeriesQueryResultVO buildLocallyDownsampledResult(SessionQueryDataSet dataSet,
                                                                  long anchorNs,
                                                                  long precisionMs,
                                                                  String aggregator) {
        TimeSeriesQueryResultVO result = new TimeSeriesQueryResultVO();
        if (isDataSetEmpty(dataSet) || precisionMs <= 0) {
            result.setTimestamps(List.of());
            result.setSeries(List.of());
            return result;
        }

        long[] keys = dataSet.getKeys();
        List<List<Object>> rows = dataSet.getValues();
        List<String> paths = dataSet.getPaths();
        int rowCount = Math.min(keys.length, rows.size());
        long precisionNs = TimeParser.toNano(precisionMs);
        LinkedHashMap<Long, List<LocalDownsampleBucket>> buckets = new LinkedHashMap<>();
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            long bucketStart = resolveBucketStartNs(keys[rowIndex], anchorNs, precisionNs);
            List<LocalDownsampleBucket> bucketColumns = buckets.computeIfAbsent(
                bucketStart,
                ignored -> createBucketColumns(bucketStart, paths.size())
            );
            List<Object> row = rows.get(rowIndex);
            for (int columnIndex = 0; columnIndex < paths.size(); columnIndex++) {
                Object value = columnIndex < row.size() ? row.get(columnIndex) : null;
                LocalDownsampleBucket updatedBucket = bucketColumns.get(columnIndex).accept(value, toDouble(value));
                bucketColumns.set(columnIndex, updatedBucket);
            }
        }

        List<Long> timestamps = new ArrayList<>();
        for (Long bucketStart : buckets.keySet()) {
            timestamps.add(TimeParser.toMillis(bucketStart));
        }

        String normalizedAggregator = normalizeAggregator(aggregator);
        List<TimeSeriesSeriesVO> series = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < paths.size(); columnIndex++) {
            TimeSeriesSeriesVO item = new TimeSeriesSeriesVO();
            item.setPath(paths.get(columnIndex));
            List<Object> values = new ArrayList<>();
            for (List<LocalDownsampleBucket> bucketColumns : buckets.values()) {
                values.add(bucketColumns.get(columnIndex).aggregate(normalizedAggregator));
            }
            item.setValues(values);
            series.add(item);
        }

        result.setTimestamps(timestamps);
        result.setSeries(series);
        return result;
    }

    /**
     * 创建单个时间桶对应的所有列聚合容器。
     */
    private List<LocalDownsampleBucket> createBucketColumns(long bucketStartNs, int columnCount) {
        List<LocalDownsampleBucket> buckets = new ArrayList<>();
        for (int i = 0; i < columnCount; i++) {
            buckets.add(new LocalDownsampleBucket(bucketStartNs));
        }
        return buckets;
    }

    /**
     * 判断降采样异常是否允许回退到本地聚合。
     */
    private boolean shouldFallbackToLocalDownsample(Long precisionMs, BizException ex) {
        return precisionMs != null
            && precisionMs > 0
            && isUnsupportedDownsampleError(ex);
    }

    /**
     * 判断降采样结果是否存在明显错误语义，命中后回退到本地聚合。
     */
    private boolean shouldFallbackToLocalDownsample(Long precisionMs,
                                                    String aggregator,
                                                    SessionQueryDataSet dataSet) {
        return precisionMs != null
            && precisionMs > 0
            && isSuspiciousDownsampleResult(dataSet, aggregator);
    }

    /**
     * 判断异常是否属于 IGinX 原生降采样不兼容场景。
     */
    private boolean isUnsupportedDownsampleError(BizException ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return false;
        }
        String message = ex.getMessage().trim().toLowerCase(Locale.ROOT);
        return message.contains("mapping function")
            || message.contains("aggregate function")
            || message.contains("aggregate type")
            || message.contains("set aggregate")
            || (message.contains("downsample") && (message.contains("not support")
            || message.contains("unsupported")
            || message.contains("failed")
            || message.contains("error")));
    }

    /**
     * 判断降采样返回值是否疑似把时间戳错当成聚合值返回。
     */
    private boolean isSuspiciousDownsampleResult(SessionQueryDataSet dataSet, String aggregator) {
        if (isDataSetEmpty(dataSet) || !requiresSuspiciousResultValidation(aggregator)) {
            return false;
        }
        long[] keys = dataSet.getKeys();
        List<List<Object>> rows = dataSet.getValues();
        int rowCount = Math.min(keys.length, rows.size());
        int comparableCount = 0;
        int matchedCount = 0;
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            List<Object> row = rows.get(rowIndex);
            if (row == null || row.isEmpty()) {
                continue;
            }
            for (Object value : row) {
                Long numericValue = tryConvertToLong(value);
                if (numericValue == null) {
                    continue;
                }
                comparableCount++;
                if (numericValue == keys[rowIndex]) {
                    matchedCount++;
                }
            }
        }
        return comparableCount > 0 && matchedCount == comparableCount;
    }

    /**
     * 仅对已知存在异常表现的聚合器做额外结果校验。
     */
    private boolean requiresSuspiciousResultValidation(String aggregator) {
        return switch (normalizeAggregator(aggregator)) {
            case "MAX", "MIN", "COUNT", "FIRST", "LAST" -> true;
            default -> false;
        };
    }

    /**
     * 尝试把对象精确转换为 Long，失败时返回 null。
     */
    private Long tryConvertToLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        if (value instanceof Float || value instanceof Double) {
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number) || number != Math.rint(number)
                || number < Long.MIN_VALUE || number > Long.MAX_VALUE) {
                return null;
            }
            return (long) number;
        }
        if (value instanceof byte[] bytes) {
            return parseLongSafely(new String(bytes, StandardCharsets.UTF_8));
        }
        if (value instanceof String text) {
            return parseLongSafely(text);
        }
        return null;
    }

    /**
     * 安全解析 Long。
     */
    private Long parseLongSafely(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 判断数据集是否为空。
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
     * 判断路径是否需要补 root 前缀。
     */
    private boolean needsRootPrefix(List<String> paths) {
        for (String path : paths) {
            if (path != null && !path.isBlank() && !path.startsWith("root.")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 为路径补充 root 前缀。
     */
    private List<String> addRootPrefix(List<String> paths) {
        List<String> result = new ArrayList<>();
        for (String path : paths) {
            if (path == null || path.isBlank()) {
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
     * 计算桶起始时间。
     */
    private long resolveBucketStartNs(long keyNs, long anchorNs, long precisionNs) {
        if (precisionNs <= 0 || keyNs <= anchorNs) {
            return anchorNs;
        }
        long offset = keyNs - anchorNs;
        return anchorNs + Math.floorDiv(offset, precisionNs) * precisionNs;
    }

    /**
     * 推断结果集中前置元数据列数量。
     */
    private int resolveValueColumnOffset(long[] keys, List<List<Object>> rows, int pathCount) {
        if (keys == null || rows == null || rows.isEmpty() || pathCount < 0) {
            return 0;
        }
        int maxRowSize = 0;
        for (List<Object> row : rows) {
            if (row != null) {
                maxRowSize = Math.max(maxRowSize, row.size());
            }
        }
        int extraColumnCount = Math.max(0, maxRowSize - Math.max(pathCount, 0));
        if (extraColumnCount <= 0) {
            return 0;
        }
        if (isLeadingTimestampColumn(keys, rows, 0)) {
            return 1;
        }
        return 0;
    }

    /**
     * 判断首列是否为重复的时间戳元数据列。
     */
    private boolean isLeadingTimestampColumn(long[] keys, List<List<Object>> rows, int columnIndex) {
        int comparableCount = 0;
        int matchedCount = 0;
        int rowCount = Math.min(keys.length, rows.size());
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            List<Object> row = rows.get(rowIndex);
            if (row == null || columnIndex >= row.size()) {
                continue;
            }
            Long numericValue = tryConvertToLong(row.get(columnIndex));
            if (numericValue == null) {
                continue;
            }
            comparableCount++;
            if (numericValue == keys[rowIndex]) {
                matchedCount++;
            }
        }
        return comparableCount > 0 && matchedCount == comparableCount;
    }

    /**
     * 根据列偏移读取真实值列。
     */
    private Object resolveColumnValue(List<Object> row, int columnIndex, int valueColumnOffset) {
        if (row == null) {
            return null;
        }
        int shiftedIndex = columnIndex + Math.max(0, valueColumnOffset);
        if (shiftedIndex >= 0 && shiftedIndex < row.size()) {
            return row.get(shiftedIndex);
        }
        if (columnIndex >= 0 && columnIndex < row.size()) {
            return row.get(columnIndex);
        }
        return null;
    }

    /**
     * 构建规范化路径索引，兼容聚合包装与 root 前缀差异。
     */
    private Map<String, Integer> buildNormalizedPathIndex(List<String> paths) {
        Map<String, Integer> normalizedIndex = new LinkedHashMap<>();
        if (paths == null) {
            return normalizedIndex;
        }
        for (int index = 0; index < paths.size(); index++) {
            normalizedIndex.putIfAbsent(normalizeMatchKey(paths.get(index)), index);
        }
        return normalizedIndex;
    }

    /**
     * 定位请求路径在结果集中的列索引。
     */
    private int resolveDataPathIndex(List<String> dataPaths, Map<String, Integer> normalizedIndex, String path) {
        if (dataPaths == null || dataPaths.isEmpty() || path == null || path.isBlank()) {
            return -1;
        }
        int index = dataPaths.indexOf(path);
        if (index >= 0) {
            return index;
        }
        Integer mapped = normalizedIndex.get(normalizeMatchKey(path));
        return mapped == null ? -1 : mapped;
    }

    /**
     * 规范化路径用于匹配（去除 root 前缀与聚合函数包装）。
     */
    private String normalizeMatchKey(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String trimmed = path.trim();
        while (!trimmed.isBlank()) {
            boolean changed = false;
            if (trimmed.startsWith("root.")) {
                trimmed = trimmed.substring("root.".length()).trim();
                changed = true;
            }
            int leftParenthesis = trimmed.indexOf('(');
            int rightParenthesis = trimmed.lastIndexOf(')');
            if (leftParenthesis > 0 && rightParenthesis == trimmed.length() - 1) {
                String functionName = trimmed.substring(0, leftParenthesis).trim();
                if (isAggregatePathWrapper(functionName)) {
                    trimmed = trimmed.substring(leftParenthesis + 1, rightParenthesis).trim();
                    changed = true;
                    continue;
                }
            }
            if (!changed) {
                break;
            }
        }
        return trimmed;
    }

    /**
     * 判断路径是否被聚合函数包装。
     */
    private boolean isAggregatePathWrapper(String functionName) {
        if (functionName == null || functionName.isBlank()) {
            return false;
        }
        for (int i = 0; i < functionName.length(); i++) {
            char current = functionName.charAt(i);
            if (!Character.isLetterOrDigit(current) && current != '_' && !Character.isWhitespace(current)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 规范化聚合器名称。
     */
    private String normalizeAggregator(String aggregator) {
        if (aggregator == null || aggregator.isBlank()) {
            return "AVG";
        }
        return aggregator.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 将值转换为 Double。
     */
    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof byte[] bytes) {
            return parseDoubleSafely(new String(bytes, StandardCharsets.UTF_8));
        }
        if (value instanceof String text) {
            return parseDoubleSafely(text);
        }
        return null;
    }

    /**
     * 安全解析数值。
     */
    private Double parseDoubleSafely(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 查询结构化表结构。
     *
     * <p>按 IGinX 用户手册建议，使用 SHOW COLUMNS 表路径.* 获取列名与类型。</p>
     *
     * @param rawTablePath 表路径（例如 rt.user 或 task.result.demoTask）
     * @return 结构化表结构
     */
    @Override
    public StructuredSchemaVO queryStructuredSchema(String rawTablePath) {
        StructuredTablePath tablePath = normalizeStructuredTablePath(rawTablePath);
        Map<String, DataType> columnTypes = structuredQueryHelper.loadColumnTypesByTablePath(tablePath.sqlPath());
        if (columnTypes == null || columnTypes.isEmpty()) {
            throw BizException.badRequest("表结构不存在或无字段");
        }

        List<StructuredSchemaColumnVO> columns = new ArrayList<>();
        for (Map.Entry<String, DataType> entry : columnTypes.entrySet()) {
            StructuredSchemaColumnVO column = new StructuredSchemaColumnVO();
            column.setName(entry.getKey());
            column.setType(entry.getValue() == null ? DataType.BINARY.name() : entry.getValue().name());
            columns.add(column);
        }

        StructuredSchemaVO schema = new StructuredSchemaVO();
        schema.setTablePath(tablePath.displayPath());
        schema.setColumns(columns);
        return schema;
    }

    /**
     * 查询结构化表数据。
     *
     * <p>实现策略：</p>
     * <p>1. 校验并规范化表路径；</p>
     * <p>2. 读取列类型，用于条件值类型转换；</p>
     * <p>3. 直接拼接 SELECT * ... WHERE ... ORDER BY ... LIMIT ... OFFSET ...；</p>
     * <p>4. 执行 COUNT(*) 获取分页总数。</p>
     *
     * @param request 查询请求
     * @return 结构化查询结果
     */
    @Override
    public StructuredQueryResultVO queryStructured(StructuredQueryRequest request) {
        try {
            StructuredTablePath tablePath = normalizeStructuredTablePath(request.getTablePath());
            Map<String, DataType> columnTypes = structuredQueryHelper.loadColumnTypesByTablePath(tablePath.sqlPath());
            if (columnTypes == null || columnTypes.isEmpty()) {
                throw BizException.badRequest("表结构不存在或无字段");
            }

            Set<String> allowedColumns = new LinkedHashSet<>(columnTypes.keySet());
            allowedColumns.add("KEY");
            allowedColumns.add(IginxStructuredUtils.INTERNAL_KEY);

            Map<String, Integer> sqlTypeMap = new LinkedHashMap<>(IginxStructuredUtils.mapIginxTypesToSqlTypes(columnTypes));
            sqlTypeMap.put("KEY", Types.BIGINT);
            sqlTypeMap.put(IginxStructuredUtils.INTERNAL_KEY, Types.BIGINT);

            List<StructuredQueryCondition> normalizedConditions = normalizeStructuredConditions(
                request.getConditions(), allowedColumns);

            StructuredSqlBuilder.SqlWithParams where = structuredSqlBuilder.buildWhereClause(
                normalizedConditions, allowedColumns, sqlTypeMap);
            String whereSql = where.sql();
            String orderBySql = buildOrderBy(request.getOrderBy(), request.getOrderDirection(), columnTypes.keySet());

            int pageNum = safePageNum(request.getPageNum());
            int pageSize = safePageSize(request.getPageSize());
            long offset = (long) (pageNum - 1) * pageSize;

            String dataSqlTemplate = "SELECT * FROM " + tablePath.sqlPath()
                + whereSql + orderBySql
                + " LIMIT " + pageSize + " OFFSET " + offset;
            String dataSql = IginxStructuredUtils.renderSqlWithParams(dataSqlTemplate, where.params());

            QueryDataSet dataSet = structuredQueryHelper.executeQuery(dataSql, pageSize);
            List<String> headers;
            List<Map<String, Object>> records;
            try {
                headers = IginxStructuredUtils.normalizeStructuredHeaders(dataSet.getColumnList());
                records = structuredQueryHelper.readAll(dataSet, headers);
            } finally {
                closeQuietly(dataSet);
            }

            long total = queryStructuredTotal(tablePath.sqlPath(), whereSql, where.params());
            StructuredQueryResultVO result = new StructuredQueryResultVO();
            result.setColumns(headers);
            result.setPage(PageResult.of(records, total, pageNum, pageSize));
            return result;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.internal("结构化查询失败: " + ex.getMessage());
        }
    }

    /**
     * 执行 COUNT(*) 获取总记录数。
     */
    private long queryStructuredTotal(String tableSqlPath, String whereSql, List<Object> params) {
        String countSqlTemplate = "SELECT COUNT(*) FROM " + tableSqlPath + whereSql;
        String countSql = IginxStructuredUtils.renderSqlWithParams(countSqlTemplate, params);
        QueryDataSet countSet = structuredQueryHelper.executeQuery(countSql, 1);
        try {
            Object[] row = nextRowQuietly(countSet);
            if (row == null || row.length == 0) {
                return 0L;
            }
            for (Object value : row) {
                long parsed = parseCountValue(value);
                if (parsed >= 0) {
                    return parsed;
                }
            }
            return 0L;
        } finally {
            closeQuietly(countSet);
        }
    }

    /**
     * 解析 COUNT(*) 返回值。
     *
     * @param value 原始值
     * @return 非负总数；无法解析时返回 -1
     */
    private long parseCountValue(Object value) {
        if (value == null) {
            return -1L;
        }
        if (value instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        if (value instanceof byte[] bytes) {
            return parseCountText(new String(bytes, StandardCharsets.UTF_8));
        }
        return parseCountText(String.valueOf(value));
    }

    /**
     * 解析文本计数。
     */
    private long parseCountText(String text) {
        if (text == null || text.isBlank()) {
            return -1L;
        }
        try {
            return Math.max(0L, Long.parseLong(text.trim()));
        } catch (Exception ex) {
            return -1L;
        }
    }

    /**
     * 构建 ORDER BY 子句。
     */
    /**
     * 归一化结构化查询条件字段名：
     * 1. 忽略大小写匹配真实列名；
     * 2. 将 KEY/_iginx_key 统一为 KEY。
     */
    private List<StructuredQueryCondition> normalizeStructuredConditions(List<StructuredQueryCondition> conditions,
                                                                         Set<String> allowedColumns) {
        if (conditions == null || conditions.isEmpty()) {
            return List.of();
        }
        Map<String, String> canonicalFieldMap = new LinkedHashMap<>();
        if (allowedColumns != null) {
            for (String column : allowedColumns) {
                if (column == null || column.isBlank()) {
                    continue;
                }
                String key = column.trim().toLowerCase(Locale.ROOT);
                canonicalFieldMap.putIfAbsent(key, column.trim());
            }
        }
        canonicalFieldMap.putIfAbsent("key", "KEY");
        canonicalFieldMap.putIfAbsent(IginxStructuredUtils.INTERNAL_KEY.toLowerCase(Locale.ROOT), "KEY");

        List<StructuredQueryCondition> normalized = new ArrayList<>();
        for (StructuredQueryCondition condition : conditions) {
            if (condition == null || condition.getField() == null || condition.getField().isBlank()) {
                continue;
            }
            String rawField = condition.getField().trim();
            String mappedField = canonicalFieldMap.get(rawField.toLowerCase(Locale.ROOT));
            if (mappedField == null || mappedField.isBlank()) {
                continue;
            }
            StructuredQueryCondition item = new StructuredQueryCondition();
            item.setLogic(condition.getLogic());
            item.setField("KEY".equalsIgnoreCase(mappedField) ? "KEY" : mappedField);
            item.setOp(condition.getOp());
            item.setValue(condition.getValue());
            normalized.add(item);
        }
        return normalized;
    }

    private String buildOrderBy(String orderBy, String direction, Set<String> columns) {
        if (orderBy == null || orderBy.isBlank()) {
            return "";
        }
        String matchedColumn = findMatchedColumn(orderBy, columns);
        if (matchedColumn == null) {
            return "";
        }
        return " ORDER BY " + IginxStructuredUtils.quoteIdentifier(matchedColumn)
            + " " + IginxStructuredUtils.normalizeOrderDirection(direction);
    }

    /**
     * 忽略大小写匹配字段名。
     */
    private String findMatchedColumn(String orderBy, Set<String> columns) {
        if (columns == null || columns.isEmpty()) {
            return null;
        }
        for (String column : columns) {
            if (column != null && column.equalsIgnoreCase(orderBy)) {
                return column;
            }
        }
        return null;
    }

    /**
     * 规范化结构化表路径。
     *
     * <p>输出两个版本：</p>
     * <p>1. displayPath：返回给前端展示，形如 rt.user 或 task.result.demoTask；</p>
     * <p>2. sqlPath：拼 SQL 使用，必要时自动加反引号。</p>
     */
    private StructuredTablePath normalizeStructuredTablePath(String rawTablePath) {
        if (rawTablePath == null || rawTablePath.isBlank()) {
            throw BizException.badRequest("IGinX 表路径不能为空");
        }
        List<String> segments = IginxStructuredUtils.splitPathSegments(rawTablePath.trim());
        // 兼容传入 rt.xxx.* 的场景：末尾 * 代表列通配符，不属于表路径本体。
        if (!segments.isEmpty() && "*".equals(segments.get(segments.size() - 1))) {
            segments = new ArrayList<>(segments.subList(0, segments.size() - 1));
        }
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                throw BizException.badRequest("IGinX 表路径格式错误");
            }
            if (segment.contains("*")) {
                throw BizException.badRequest("IGinX 表路径不能包含通配符");
            }
        }

        if (isRtStructuredTablePath(segments)) {
            if (segments.size() < 2) {
                throw BizException.badRequest("IGinX 表路径格式错误，应为 rt.xxx");
            }
        } else if (isTaskResultStructuredTablePath(segments)) {
            if (segments.size() < 3) {
                throw BizException.badRequest("IGinX 任务结果表路径格式错误，应为 task.result.<taskId>");
            }
        } else {
            throw BizException.badRequest("结构化查询仅支持 rt.* 或 task.result.<taskId> 路径");
        }

        String displayPath = String.join(".", segments);
        String sqlPath = segments.stream()
            .map(IginxStructuredUtils::quoteIdentifier)
            .collect(Collectors.joining("."));
        return new StructuredTablePath(displayPath, sqlPath);
    }

    /**
     * 判断是否为 rt 结构化表路径。
     */
    private boolean isRtStructuredTablePath(List<String> segments) {
        return segments != null
            && !segments.isEmpty()
            && RT_PREFIX.equalsIgnoreCase(segments.get(0));
    }

    /**
     * 判断是否为 task 结构化结果表路径。
     */
    private boolean isTaskResultStructuredTablePath(List<String> segments) {
        return segments != null
            && segments.size() >= 2
            && TASK_PREFIX.equalsIgnoreCase(segments.get(0))
            && TASK_RESULT_SEGMENT.equalsIgnoreCase(segments.get(1));
    }

    /**
     * 安全读取页码。
     */
    private int safePageNum(Integer pageNum) {
        if (pageNum == null || pageNum < 1) {
            return 1;
        }
        return pageNum;
    }

    /**
     * 安全读取分页大小。
     */
    private int safePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 50;
        }
        return Math.min(pageSize, 500);
    }

    /**
     * 安全读取下一行。
     */
    private Object[] nextRowQuietly(QueryDataSet dataSet) {
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
     * 安静关闭结果集。
     */
    private void closeQuietly(QueryDataSet dataSet) {
        if (dataSet == null) {
            return;
        }
        try {
            dataSet.close();
        } catch (Exception ignored) {
        }
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
     * 本地降采样桶。
     *
     * @param bucketStartNs 桶起始时间（纳秒）
     * @param nonNullCount 非空值数量
     * @param numericCount 数值数量
     * @param sum 数值和
     * @param min 最小值
     * @param max 最大值
     * @param firstValue 首个数值
     * @param lastValue 最后一个数值
     */
    private record LocalDownsampleBucket(long bucketStartNs,
                                         long nonNullCount,
                                         long numericCount,
                                         double sum,
                                         Double min,
                                         Double max,
                                         Double firstValue,
                                         Double lastValue) {

        private LocalDownsampleBucket(long bucketStartNs) {
            this(bucketStartNs, 0L, 0L, 0D, null, null, null, null);
        }

        private LocalDownsampleBucket accept(Object rawValue, Double numericValue) {
            long nextNonNullCount = rawValue == null ? nonNullCount : nonNullCount + 1;
            long nextNumericCount = numericValue == null ? numericCount : numericCount + 1;
            double nextSum = numericValue == null ? sum : sum + numericValue;
            Double nextMin = numericValue == null
                ? min
                : (min == null ? numericValue : Math.min(min, numericValue));
            Double nextMax = numericValue == null
                ? max
                : (max == null ? numericValue : Math.max(max, numericValue));
            Double nextFirstValue = firstValue != null ? firstValue : numericValue;
            Double nextLastValue = numericValue != null ? numericValue : lastValue;
            return new LocalDownsampleBucket(
                bucketStartNs,
                nextNonNullCount,
                nextNumericCount,
                nextSum,
                nextMin,
                nextMax,
                nextFirstValue,
                nextLastValue
            );
        }

        private Double aggregate(String aggregator) {
            return switch (aggregator) {
                case "MAX" -> max;
                case "MIN" -> min;
                case "SUM" -> numericCount > 0 ? sum : null;
                case "COUNT" -> (double) nonNullCount;
                case "FIRST" -> firstValue;
                case "LAST" -> lastValue;
                default -> numericCount > 0 ? sum / numericCount : null;
            };
        }
    }

    /**
     * 结构化表路径封装。
     */
    private record StructuredTablePath(String displayPath, String sqlPath) {
    }
}
