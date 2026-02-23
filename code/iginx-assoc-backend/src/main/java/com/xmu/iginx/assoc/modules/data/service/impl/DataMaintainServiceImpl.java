package com.xmu.iginx.assoc.modules.data.service.impl;

import cn.edu.tsinghua.iginx.thrift.DataType;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.dto.StructuredRowCreateRequest;
import com.xmu.iginx.assoc.modules.data.dto.StructuredRowDeleteRequest;
import com.xmu.iginx.assoc.modules.data.dto.StructuredRowUpdateRequest;
import com.xmu.iginx.assoc.modules.data.dto.TimeSeriesDeleteRequest;
import com.xmu.iginx.assoc.modules.data.enums.DataSourceType;
import com.xmu.iginx.assoc.modules.data.model.DataSourceDetail;
import com.xmu.iginx.assoc.modules.data.service.DataMaintainService;
import com.xmu.iginx.assoc.modules.data.service.DataSourceAccessor;
import com.xmu.iginx.assoc.modules.data.util.IginxDataTypeConverter;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredQueryHelper;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredUtils;
import com.xmu.iginx.assoc.modules.data.util.StructuredKeyGenerator;
import com.xmu.iginx.assoc.modules.data.util.TimeParser;
import com.xmu.iginx.assoc.modules.data.util.TimeSeriesPathUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DataMaintainServiceImpl implements DataMaintainService {

    private final DataSourceAccessor dataSourceAccessor;
    private final IginxStorageWrapper iginxStorageWrapper;
    private final IginxStructuredQueryHelper structuredQueryHelper;

    @Override
    public void deleteTimeSeries(TimeSeriesDeleteRequest request) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.INFLUXDB, DataSourceType.IOTDB);
        if (request.getPaths() == null || request.getPaths().isEmpty()) {
            throw BizException.badRequest("测点路径不能为空");
        }
        if (request.getTimeRange() == null) {
            throw BizException.badRequest("时间范围不能为空");
        }
        if (!"delete".equalsIgnoreCase(request.getOperation())) {
            throw BizException.badRequest("暂不支持的时序操作类型");
        }
        List<String> paths = TimeSeriesPathUtils.resolvePathsUnderMount(request.getPaths(), detail.entity().getMountPath(), true);
        long startNs = TimeParser.toNano(TimeParser.parseToMillis(request.getTimeRange().getStart(), null));
        long endNs = TimeParser.toNano(TimeParser.parseToMillis(request.getTimeRange().getEnd(), null));
        iginxStorageWrapper.executeWithSession(session -> {
            session.deleteDataInColumns(paths, startNs, endNs);
            return null;
        });
    }

    @Override
    public void createStructuredRow(StructuredRowCreateRequest request) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.POSTGRESQL);
        Map<String, Object> rawData = request.getData();
        if (rawData == null || rawData.isEmpty()) {
            throw BizException.badRequest("数据不能为空");
        }
        String schemaWithMount = IginxStructuredUtils.mergeMountPath(detail.entity().getMountPath(), request.getSchema());
        Map<String, DataType> columnTypes = requireColumnTypes(schemaWithMount, request.getTable());
        Map<String, Object> data = normalizeData(rawData);
        if (data.isEmpty()) {
            throw BizException.badRequest("数据不能为空");
        }
        long key = resolveCreateKey(rawData);
        String sql = buildInsertSql(schemaWithMount, request.getTable(), key, data, columnTypes);
        try {
            structuredQueryHelper.executeSql(sql);
        } catch (Exception ex) {
            throw BizException.internal("新增结构化数据失败: " + ex.getMessage());
        }
    }

    @Override
    public void updateStructuredRow(StructuredRowUpdateRequest request) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.POSTGRESQL);
        Map<String, Object> rawData = request.getData();
        if (rawData == null || rawData.isEmpty()) {
            throw BizException.badRequest("数据不能为空");
        }
        long key = resolveRequiredKey(rawData);
        String schemaWithMount = IginxStructuredUtils.mergeMountPath(detail.entity().getMountPath(), request.getSchema());
        Map<String, DataType> columnTypes = requireColumnTypes(schemaWithMount, request.getTable());
        Map<String, Object> data = normalizeData(rawData);
        if (data.isEmpty()) {
            throw BizException.badRequest("未提供可更新字段");
        }
        String sql = buildInsertSql(schemaWithMount, request.getTable(), key, data, columnTypes);
        try {
            structuredQueryHelper.executeSql(sql);
        } catch (Exception ex) {
            throw BizException.internal("更新结构化数据失败: " + ex.getMessage());
        }
    }

    @Override
    public void deleteStructuredRow(StructuredRowDeleteRequest request) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.POSTGRESQL);
        Map<String, Object> keys = request.getKeys();
        long key = resolveDeleteKey(keys);
        String schemaWithMount = IginxStructuredUtils.mergeMountPath(detail.entity().getMountPath(), request.getSchema());
        requireColumnTypes(schemaWithMount, request.getTable());
        String tablePath = IginxStructuredUtils.buildTablePath(schemaWithMount, request.getTable());
        String sql = "DELETE FROM " + tablePath + " WHERE KEY = " + key;
        try {
            structuredQueryHelper.executeSql(sql);
        } catch (Exception ex) {
            throw BizException.internal("删除结构化数据失败: " + ex.getMessage());
        }
    }

    private Map<String, DataType> requireColumnTypes(String schema, String table) {
        Map<String, DataType> columnTypes = structuredQueryHelper.loadColumnTypes(schema, table);
        if (columnTypes == null || columnTypes.isEmpty()) {
            throw BizException.badRequest("表结构不存在或无字段");
        }
        return columnTypes;
    }

    private Map<String, Object> normalizeData(Map<String, Object> data) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (data == null) {
            return normalized;
        }
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            if (IginxStructuredUtils.isInternalKey(entry.getKey())) {
                continue;
            }
            normalized.put(entry.getKey(), entry.getValue());
        }
        return normalized;
    }

    private void validateColumns(Iterable<String> columns, Map<String, DataType> columnTypes) {
        for (String column : columns) {
            if (!columnTypes.containsKey(column)) {
                throw BizException.badRequest("字段不存在: " + column);
            }
        }
    }

    private long resolveCreateKey(Map<String, Object> data) {
        Long internal = StructuredKeyGenerator.extractInternalKey(data);
        if (internal != null) {
            if (IginxStructuredUtils.isReservedKey(internal)) {
                throw BizException.badRequest("内部键 _iginx_key 不合法");
            }
            if (internal < 0) {
                throw BizException.badRequest("内部键 _iginx_key 不能为负数");
            }
            return internal;
        }
        return StructuredKeyGenerator.randomKey();
    }

    private long resolveRequiredKey(Map<String, Object> data) {
        Long internal = StructuredKeyGenerator.extractInternalKey(data);
        if (internal == null) {
            throw BizException.badRequest("缺少内部键 _iginx_key，无法更新");
        }
        if (IginxStructuredUtils.isReservedKey(internal)) {
            throw BizException.badRequest("内部键 _iginx_key 不合法");
        }
        if (internal < 0) {
            throw BizException.badRequest("内部键 _iginx_key 不能为负数");
        }
        return internal;
    }

    private long resolveDeleteKey(Map<String, Object> keys) {
        if (keys == null || keys.isEmpty()) {
            throw BizException.badRequest("删除条件不能为空");
        }
        Long internal = StructuredKeyGenerator.extractInternalKey(keys);
        long key = internal != null ? internal : StructuredKeyGenerator.hashKey(keys);
        if (IginxStructuredUtils.isReservedKey(key)) {
            throw BizException.badRequest("内部键 _iginx_key 不合法");
        }
        if (key < 0) {
            throw BizException.badRequest("内部键 _iginx_key 不能为负数");
        }
        return key;
    }

    private String buildInsertSql(String schema, String table, long key,
                                 Map<String, Object> data,
                                 Map<String, DataType> columnTypes) {
        List<String> columns = new ArrayList<>(data.keySet());
        validateColumns(columns, columnTypes);
        StringBuilder builder = new StringBuilder();
        builder.append("INSERT INTO ")
            .append(IginxStructuredUtils.buildTablePath(schema, table))
            .append(" (KEY");
        for (String column : columns) {
            builder.append(", ")
                .append(IginxStructuredUtils.buildColumnPath(schema, table, column));
        }
        builder.append(") VALUES (").append(key);
        for (String column : columns) {
            DataType type = columnTypes.getOrDefault(column, DataType.BINARY);
            Object value = coerceValue(data.get(column), type);
            builder.append(", ").append(IginxStructuredUtils.toSqlLiteral(value));
        }
        builder.append(")");
        return builder.toString();
    }

    private Object coerceValue(Object raw, DataType type) {
        if (raw == null) {
            return null;
        }
        if (type == DataType.BINARY) {
            if (raw instanceof byte[] bytes) {
                return bytes;
            }
            return String.valueOf(raw).getBytes(StandardCharsets.UTF_8);
        }
        if (raw instanceof Number number) {
            return switch (type) {
                case INTEGER -> number.intValue();
                case LONG -> number.longValue();
                case FLOAT -> number.floatValue();
                case DOUBLE -> number.doubleValue();
                case BOOLEAN -> number.intValue() != 0;
                case BINARY -> String.valueOf(raw).getBytes(StandardCharsets.UTF_8);
            };
        }
        if (raw instanceof Boolean bool && type == DataType.BOOLEAN) {
            return bool;
        }
        return IginxDataTypeConverter.parseValue(String.valueOf(raw), type);
    }
}

