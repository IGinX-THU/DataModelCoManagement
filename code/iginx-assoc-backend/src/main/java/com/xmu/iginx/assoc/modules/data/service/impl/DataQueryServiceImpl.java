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
import com.xmu.iginx.assoc.modules.data.model.DataSourceDetail;
import com.xmu.iginx.assoc.modules.data.service.DataQueryService;
import com.xmu.iginx.assoc.modules.data.service.DataSourceAccessor;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DataQueryServiceImpl implements DataQueryService {

    private final DataSourceAccessor dataSourceAccessor;
    private final IginxStructuredQueryHelper structuredQueryHelper;
    private final IginxStorageWrapper iginxStorageWrapper;
    private final StructuredSqlBuilder structuredSqlBuilder = new StructuredSqlBuilder();

    @Override
    public TimeSeriesQueryResultVO queryTimeSeries(TimeSeriesQueryRequest request) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.INFLUXDB, DataSourceType.IOTDB);
        if (request.getPaths() == null || request.getPaths().isEmpty()) {
            throw BizException.badRequest("测点路径不能为空");
        }
        if (request.getTimeRange() == null) {
            throw BizException.badRequest("时间范围不能为空");
        }
        List<String> resolvedPaths = TimeSeriesPathUtils.resolvePathsUnderMount(request.getPaths(), detail.entity().getMountPath(), true);
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

    @Override
    public StructuredQueryResultVO queryStructured(StructuredQueryRequest request) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.POSTGRESQL);
        try {
            String schemaWithMount = IginxStructuredUtils.mergeMountPath(detail.entity().getMountPath(), request.getSchema());
            Map<String, DataType> columnTypes = structuredQueryHelper.loadColumnTypes(schemaWithMount, request.getTable());
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
            String tablePath = IginxStructuredUtils.buildTablePath(schemaWithMount, request.getTable());
            String whereClause = appendKeyFilter(rewriteInternalKey(where.sql()));
            String orderBy = buildOrderBy(request.getOrderBy(), request.getOrderDirection(), columns);
            String sql = "SELECT " + selectList + " FROM " + tablePath + whereClause + orderBy;
            String finalSql = IginxStructuredUtils.renderSqlWithParams(sql, where.params());
            QueryDataSet dataSet = structuredQueryHelper.executeQuery(finalSql, request.getPageSize());
            try {
                List<String> rawHeader = dataSet.getColumnList();
                List<String> header = IginxStructuredUtils.normalizeStructuredHeaders(rawHeader);
                List<Map<String, Object>> records = structuredQueryHelper.readAll(dataSet, header);
                normalizeInternalKey(records);
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

    private boolean isDeletedStructuredRecord(Map<String, Object> record, List<String> dataColumns) {
        for (String column : dataColumns) {
            if (record.get(column) != null) {
                return false;
            }
        }
        return true;
    }

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

    private String buildOrderBy(String orderBy, String direction, Set<String> columns) {
        if (orderBy == null || orderBy.isBlank()) {
            return "";
        }
        if (IginxStructuredUtils.isInternalKey(orderBy)) {
            return " ORDER BY KEY " + IginxStructuredUtils.normalizeOrderDirection(direction);
        }
        if (!columns.contains(orderBy)) {
            return "";
        }
        String dir = IginxStructuredUtils.normalizeOrderDirection(direction);
        return " ORDER BY " + IginxStructuredUtils.quoteIdentifier(orderBy) + " " + dir;
    }

    private String appendKeyFilter(String whereSql) {
        String keyFilter = "KEY <> " + IginxStructuredUtils.DUMMY_KEY;
        if (whereSql == null || whereSql.isBlank()) {
            return " WHERE " + keyFilter;
        }
        return whereSql + " AND " + keyFilter;
    }

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

    private void closeQuietly(QueryDataSet dataSet) {
        if (dataSet == null) {
            return;
        }
        try {
            dataSet.close();
        } catch (Exception ignored) {
        }
    }

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


