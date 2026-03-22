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
        long endNs = TimeParser.toNano(TimeParser.parseToMillis(request.getTimeRange().getEnd(), null));
        SessionQueryDataSet dataSet = iginxStorageWrapper.executeWithSession(session -> {
            if (request.isDownsample() && request.getPrecisionMs() != null) {
                AggregateType aggregateType = mapAggregateType(request.getAggregator());
                return session.downsampleQuery(resolvedPaths, startNs, endNs, aggregateType,
                    TimeParser.toNano(request.getPrecisionMs()));
            }
            return session.queryData(resolvedPaths, startNs, endNs);
        });

        List<Long> timestamps = new ArrayList<>();
        for (long key : dataSet.getKeys()) {
            timestamps.add(TimeParser.toMillis(key));
        }

        List<List<Object>> values = dataSet.getValues();
        List<String> paths = dataSet.getPaths();
        List<TimeSeriesSeriesVO> series = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            TimeSeriesSeriesVO item = new TimeSeriesSeriesVO();
            item.setPath(paths.get(i));
            List<Object> columnValues = new ArrayList<>();
            for (List<Object> row : values) {
                Object value = row.get(i);
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

        TimeSeriesQueryResultVO result = new TimeSeriesQueryResultVO();
        result.setTimestamps(timestamps);
        result.setSeries(series);
        return result;
    }

    /**
     * 查询结构化表结构。
     *
     * <p>按 IGinX 用户手册建议，使用 SHOW COLUMNS rt.xxx.* 获取列名与类型。</p>
     *
     * @param rawTablePath 表路径（例如 rt.user）
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
     * <p>1. displayPath：返回给前端展示，形如 rt.user；</p>
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
        if (segments.size() < 2) {
            throw BizException.badRequest("IGinX 表路径格式错误，应为 rt.xxx");
        }
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                throw BizException.badRequest("IGinX 表路径格式错误，应为 rt.xxx");
            }
            if (segment.contains("*")) {
                throw BizException.badRequest("IGinX 表路径不能包含通配符");
            }
        }
        if (!"rt".equalsIgnoreCase(segments.get(0))) {
            throw BizException.badRequest("结构化查询仅支持 rt 路径");
        }
        String displayPath = String.join(".", segments);
        String sqlPath = segments.stream()
            .map(IginxStructuredUtils::quoteIdentifier)
            .collect(Collectors.joining("."));
        return new StructuredTablePath(displayPath, sqlPath);
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
     * 结构化表路径封装。
     */
    private record StructuredTablePath(String displayPath, String sqlPath) {
    }
}
