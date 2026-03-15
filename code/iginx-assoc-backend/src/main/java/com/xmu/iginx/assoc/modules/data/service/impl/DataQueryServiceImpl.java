package com.xmu.iginx.assoc.modules.data.service.impl;

import cn.edu.tsinghua.iginx.session.QueryDataSet;
import cn.edu.tsinghua.iginx.session.SessionQueryDataSet;
import cn.edu.tsinghua.iginx.thrift.AggregateType;
import cn.edu.tsinghua.iginx.thrift.DataType;
import com.xmu.iginx.assoc.common.PageResult;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.dto.StructuredQueryRequest;
import com.xmu.iginx.assoc.modules.data.dto.TimeSeriesQueryRequest;
import com.xmu.iginx.assoc.modules.data.enums.DataSourceType;
import com.xmu.iginx.assoc.modules.data.service.DataQueryService;
import com.xmu.iginx.assoc.modules.data.service.DataSourceAccessor;
import com.xmu.iginx.assoc.modules.data.util.DataPrefixRules;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredQueryHelper;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredUtils;
import com.xmu.iginx.assoc.modules.data.util.StructuredSqlBuilder;
import com.xmu.iginx.assoc.modules.data.util.TimeParser;
import com.xmu.iginx.assoc.modules.data.util.TimeSeriesPathUtils;
import com.xmu.iginx.assoc.modules.data.vo.StructuredQueryResultVO;
import com.xmu.iginx.assoc.modules.data.vo.TimeSeriesQueryResultVO;
import com.xmu.iginx.assoc.modules.data.vo.TimeSeriesSeriesVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 数据查询服务实现，支持时序与结构化数据查询。 */
@Service
@RequiredArgsConstructor
public class DataQueryServiceImpl implements DataQueryService {

    private final DataSourceAccessor dataSourceAccessor;
    private final IginxStructuredQueryHelper structuredQueryHelper;
    private final IginxStorageWrapper iginxStorageWrapper;
    private final StructuredSqlBuilder structuredSqlBuilder = new StructuredSqlBuilder();

    /**
     * 查询时序数据，支持可选的降采样聚合。     *
     * @param request 查询请求
     * @return 时序查询结果
     */
    @Override
    public TimeSeriesQueryResultVO queryTimeSeries(TimeSeriesQueryRequest request) {
        dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.INFLUXDB, DataSourceType.IOTDB);
        if (request.getPaths() == null || request.getPaths().isEmpty()) {
            throw BizException.badRequest("测点路径不能为空");
        }
        if (request.getTimeRange() == null) {
            throw BizException.badRequest("时间范围不能为空");
        }
        List<String> resolvedPaths = request.getPaths().stream()
            .map(TimeSeriesPathUtils::stripRootPrefix)
            .filter(path -> path != null && !path.isBlank())
            .toList();
        if (resolvedPaths.isEmpty()) {
            throw BizException.badRequest("测点路径不能为空");
        }
        // 将毫秒时间转换为纳秒，匹配 Iginx 查询接口
        long startNs = TimeParser.toNano(TimeParser.parseToMillis(request.getTimeRange().getStart(), null));
        long endNs = TimeParser.toNano(TimeParser.parseToMillis(request.getTimeRange().getEnd(), null));
        SessionQueryDataSet dataSet = iginxStorageWrapper.executeWithSession(session -> {
            if (request.isDownsample() && request.getPrecisionMs() != null) {
                // 降采样查询，按聚合方式与精度返回结果
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
                // 二进制值按字符串展示，避免前端无法解析
                if (value instanceof byte[] bytes) {
                    columnValues.add(new String(bytes));
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
     * 查询结构化数据，支持条件过滤与分页。     *
     * @param request 查询请求
     * @return 结构化查询结果     */
    @Override
    public StructuredQueryResultVO queryStructured(StructuredQueryRequest request) {
        dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.POSTGRESQL);
        try {
            // 统一结构化 schema 路径，确保与真实表路径一致
            String schemaPath = DataPrefixRules.normalizeStructuredSchema(request.getSchema());
            Map<String, DataType> columnTypes = structuredQueryHelper.loadColumnTypes(schemaPath, request.getTable());
            if (columnTypes == null || columnTypes.isEmpty()) {
                throw BizException.badRequest("表结构不存在或无字段");
            }
            Map<String, Integer> sqlTypeMap = IginxStructuredUtils.mapIginxTypesToSqlTypes(columnTypes);
            sqlTypeMap = new java.util.LinkedHashMap<>(sqlTypeMap);
            sqlTypeMap.put(IginxStructuredUtils.INTERNAL_KEY, java.sql.Types.BIGINT);
            Set<String> columns = columnTypes.keySet();
            StructuredSqlBuilder.SqlWithParams where = structuredSqlBuilder.buildWhereClause(
                request.getConditions(), sqlTypeMap.keySet(), sqlTypeMap);
            String selectList = "*";
            String tablePath = IginxStructuredUtils.buildTablePath(schemaPath, request.getTable());
            // 追加 KEY 过滤条件，避免查询内部占位行
            String whereClause = appendKeyFilter(rewriteInternalKey(where.sql()));
            String orderBy = buildOrderBy(request.getOrderBy(), request.getOrderDirection(), columns);
            String sql = "SELECT " + selectList + " FROM " + tablePath + whereClause + orderBy;
            String finalSql = IginxStructuredUtils.renderSqlWithParams(sql, where.params());
            QueryDataSet dataSet = structuredQueryHelper.executeQuery(finalSql, request.getPageSize());
            try {
                List<String> rawHeader = dataSet.getColumnList();
                List<String> header = IginxStructuredUtils.normalizeStructuredHeaders(rawHeader);
                List<Map<String, Object>> records = structuredQueryHelper.readAll(dataSet, header);
                // 统一内部键展示格式，保持前端展示一致
                normalizeInternalKey(records);
                // 过滤逻辑删除行，避免前端展示空记录
                List<Map<String, Object>> visibleRecords = filterDeletedStructuredRecords(records, header);
                List<Map<String, Object>> pageRecords = paginateStructuredRecords(
                    visibleRecords, request.getPageNum(), request.getPageSize());
                StructuredQueryResultVO result = new StructuredQueryResultVO();
                result.setColumns(header);
                result.setPage(PageResult.of(pageRecords, visibleRecords.size(), request.getPageNum(), request.getPageSize()));
                return result;
            } finally {
                closeQuietly(dataSet);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception ex) {
            throw BizException.internal("结构化查询失败: " + ex.getMessage());
        }
    }

    /**
     * 过滤结构化数据中的逻辑删除行。     *
     * @param records 鍘熷璁板綍
     * @param headers 表头
     * @return 过滤后的记录
     */
    private List<Map<String, Object>> filterDeletedStructuredRecords(List<Map<String, Object>> records,
                                                                     List<String> headers) {
        if (records == null || records.isEmpty()) {
            return records == null ? List.of() : records;
        }
        List<String> dataColumns = new ArrayList<>();
        if (headers != null) {
            for (String header : headers) {
                if (IginxStructuredUtils.isInternalKey(header)) {
                    continue;
                }
                // 仅关注业务列是否有值
                dataColumns.add(header);
            }
        }
        if (dataColumns.isEmpty()) {
            return records;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> record : records) {
            if (record == null) {
                continue;
            }
            if (!isDeletedStructuredRecord(record, dataColumns)) {
                filtered.add(record);
            }
        }
        return filtered;
    }

    /**
     * 判断结构化记录是否为逻辑删除行。     *
     * @param record 璁板綍
     * @param dataColumns 业务列     * @return 是否删除
     */
    private boolean isDeletedStructuredRecord(Map<String, Object> record, List<String> dataColumns) {
        for (String column : dataColumns) {
            if (record.get(column) != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * 对结构化记录进行内存分页。     *
     * @param records 记录列表
     * @param pageNum 椤电爜
     * @param pageSize 页大小     * @return 分页结果
     */
    private List<Map<String, Object>> paginateStructuredRecords(List<Map<String, Object>> records,
                                                                int pageNum,
                                                                int pageSize) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.max(pageSize, 1);
        long offset = (long) (safePageNum - 1) * safePageSize;
        if (offset >= records.size()) {
            return List.of();
        }
        int fromIndex = (int) offset;
        int toIndex = (int) Math.min(records.size(), offset + safePageSize);
        return new ArrayList<>(records.subList(fromIndex, toIndex));
    }

    /**
     * 构建 ORDER BY 子句。     *
     * @param orderBy 排序字段     * @param direction 排序方向
     * @param columns 有效列集合     * @return ORDER BY 子句（含前导空格）     */
    private String buildOrderBy(String orderBy, String direction, Set<String> columns) {
        if (orderBy == null || orderBy.isBlank()) {
            return "";
        }
        if (IginxStructuredUtils.isInternalKey(orderBy)) {
            // 内部键列统一映射为 KEY
            return " ORDER BY KEY " + IginxStructuredUtils.normalizeOrderDirection(direction);
        }
        if (!columns.contains(orderBy)) {
            return "";
        }
        String dir = IginxStructuredUtils.normalizeOrderDirection(direction);
        return " ORDER BY " + IginxStructuredUtils.quoteIdentifier(orderBy) + " " + dir;
    }

    /**
     * 追加 KEY 过滤条件，排除虚拟占位行。     *
     * @param whereSql 原始条件
     * @return 追加后的条件
     */
    private String appendKeyFilter(String whereSql) {
        String keyFilter = "KEY <> " + IginxStructuredUtils.DUMMY_KEY;
        if (whereSql == null || whereSql.isBlank()) {
            return " WHERE " + keyFilter;
        }
        return whereSql + " AND " + keyFilter;
    }

    /**
     * 将内部键字段重写为 KEY。     *
     * @param whereSql 原始条件
     * @return 重写后的条件
     */
    private String rewriteInternalKey(String whereSql) {
        if (whereSql == null || whereSql.isBlank()) {
            return whereSql;
        }
        String normalized = whereSql;
        String quotedInternal = IginxStructuredUtils.quoteIdentifier(IginxStructuredUtils.INTERNAL_KEY);
        normalized = normalized.replace(quotedInternal, "KEY");
        normalized = normalized.replace(IginxStructuredUtils.INTERNAL_KEY, "KEY");
        return normalized;
    }

    /**
     * 统一内部键字段为字符串类型，避免前端类型不一致。     *
     * @param records 记录列表
     */
    private void normalizeInternalKey(List<Map<String, Object>> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (Map<String, Object> record : records) {
            if (record == null) {
                continue;
            }
            Object raw = record.get(IginxStructuredUtils.INTERNAL_KEY);
            if (raw == null) {
                continue;
            }
            if (raw instanceof Number number) {
                record.put(IginxStructuredUtils.INTERNAL_KEY, String.valueOf(number.longValue()));
            } else {
                record.put(IginxStructuredUtils.INTERNAL_KEY, String.valueOf(raw));
            }
        }
    }

    /**
     * 安静关闭查询结果集。     *
     * @param dataSet 查询结果
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
     * 将聚合器字符串映射为 Iginx 聚合类型。     *
     * @param aggregator 聚合器名     * @return 聚合类型
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
}






