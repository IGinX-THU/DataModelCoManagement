package com.xmu.iginx.assoc.modules.data.service.impl;

import cn.edu.tsinghua.iginx.session.Column;
import cn.edu.tsinghua.iginx.thrift.DataType;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.dto.MeasurementRequest;
import com.xmu.iginx.assoc.modules.data.dto.StorageGroupRequest;
import com.xmu.iginx.assoc.modules.data.dto.TableCreateRequest;
import com.xmu.iginx.assoc.modules.data.dto.TableDropRequest;
import com.xmu.iginx.assoc.modules.data.dto.TableColumnDefinitionDTO;
import com.xmu.iginx.assoc.modules.data.enums.DataSourceType;
import com.xmu.iginx.assoc.modules.data.model.DataSourceDetail;
import com.xmu.iginx.assoc.modules.data.service.DataSourceAccessor;
import com.xmu.iginx.assoc.modules.data.service.StructureService;
import com.xmu.iginx.assoc.modules.data.util.IginxDataTypeConverter;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredQueryHelper;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredUtils;
import com.xmu.iginx.assoc.modules.data.util.TimeSeriesPathUtils;
import com.xmu.iginx.assoc.modules.data.vo.TableColumnVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StructureServiceImpl implements StructureService {

    private final DataSourceAccessor dataSourceAccessor;
    private final IginxStorageWrapper iginxStorageWrapper;
    private final IginxStructuredQueryHelper structuredQueryHelper;

    @Override
    public List<TableColumnVO> listTableColumns(Long sourceId, String schema, String table) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(sourceId, DataSourceType.POSTGRESQL);
        try {
            String schemaWithMount = IginxStructuredUtils.mergeMountPath(detail.entity().getMountPath(), schema);
            var columnTypes = structuredQueryHelper.loadColumnTypes(schemaWithMount, table);
            List<TableColumnVO> columns = new ArrayList<>();
            if (columnTypes == null || columnTypes.isEmpty()) {
                return columns;
            }
            for (var entry : columnTypes.entrySet()) {
                TableColumnVO column = new TableColumnVO();
                column.setName(entry.getKey());
                column.setType(toDisplayType(entry.getValue()));
                column.setNullable(true);
                column.setPrimaryKey(false);
                columns.add(column);
            }
            return columns;
        } catch (Exception ex) {
            throw BizException.internal("获取表字段失败: " + ex.getMessage());
        }
    }

    @Override
    public void createStorageGroup(StorageGroupRequest request) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.INFLUXDB, DataSourceType.IOTDB);
        String path = TimeSeriesPathUtils.resolvePathUnderMount(request.getPath(), detail.entity().getMountPath(), true);
        MeasurementRequest measurementRequest = new MeasurementRequest();
        measurementRequest.setSourceId(request.getSourceId());
        measurementRequest.setPath(TimeSeriesPathUtils.joinPath(path, "__init__"));
        measurementRequest.setDataType("DOUBLE");
        createMeasurement(measurementRequest);
    }

    @Override
    public void dropStorageGroup(StorageGroupRequest request) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.INFLUXDB, DataSourceType.IOTDB);
        String path = TimeSeriesPathUtils.resolvePathUnderMount(request.getPath(), detail.entity().getMountPath(), true);
        List<String> targets = resolveDeleteTargets(path);
        if (targets.isEmpty()) {
            return;
        }
        try {
            iginxStorageWrapper.executeWithSession(session -> {
                session.deleteColumns(targets);
                return null;
            });
        } catch (BizException ex) {
            if (isReadOnlyDeleteWarning(ex)) {
                log.warn("鍒犻櫎瀛樺偍缁勫寘鍚彧璇荤墖娈碉紝宸叉竻鐞嗗彲鍐欐暟鎹細{}", path);
                return;
            }
            throw ex;
        }
    }

    @Override
    public void createMeasurement(MeasurementRequest request) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.INFLUXDB, DataSourceType.IOTDB);
        String path = TimeSeriesPathUtils.resolvePathUnderMount(request.getPath(), detail.entity().getMountPath(), false);
        DataType dataType = IginxDataTypeConverter.parseType(request.getDataType());
        long timestamp = System.currentTimeMillis() * 1_000_000;
        Object value = defaultValue(dataType);
        iginxStorageWrapper.executeWithSession(session -> {
            session.insertRowRecords(List.of(path), new long[]{timestamp}, new Object[]{new Object[]{value}},
                List.of(dataType), null);
            return null;
        });
    }

    @Override
    public void dropMeasurement(MeasurementRequest request) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.INFLUXDB, DataSourceType.IOTDB);
        String path = TimeSeriesPathUtils.resolvePathUnderMount(request.getPath(), detail.entity().getMountPath(), false);
        try {
            iginxStorageWrapper.executeWithSession(session -> {
                session.deleteColumns(List.of(path));
                return null;
            });
        } catch (BizException ex) {
            if (isReadOnlyDeleteWarning(ex)) {
                log.warn("鍒犻櫎娴嬬偣鍖呭惈鍙鐗囨锛屽凡娓呯悊鍙啓鏁版嵁锛歿}", path);
                return;
            }
            throw ex;
        }
    }

    @Override
    public void createTable(TableCreateRequest request) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.POSTGRESQL);
        if (request.getColumns() == null || request.getColumns().isEmpty()) {
            throw BizException.badRequest("建表字段不能为空");
        }
        String schemaWithMount = IginxStructuredUtils.mergeMountPath(detail.entity().getMountPath(), request.getSchema());
        Map<String, DataType> existing = structuredQueryHelper.loadColumnTypes(schemaWithMount, request.getTable());
        if (existing != null && !existing.isEmpty()) {
            throw BizException.badRequest("表已存在");
        }
        Map<String, DataType> columnTypes = new LinkedHashMap<>();
        for (TableColumnDefinitionDTO column : request.getColumns()) {
            if (column.getName() == null || column.getName().isBlank()) {
                continue;
            }
            DataType type = resolveStructuredType(column.getType());
            columnTypes.put(column.getName(), type);
        }
        if (columnTypes.isEmpty()) {
            throw BizException.badRequest("建表字段不能为空");
        }
        String sql = buildCreateTableSql(schemaWithMount, request.getTable(), columnTypes);
        try {
            structuredQueryHelper.executeSql(sql);
        } catch (Exception ex) {
            throw BizException.internal("创建表失败: " + ex.getMessage());
        }
    }

    @Override
    public void dropTable(TableDropRequest request) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.POSTGRESQL);
        String schemaWithMount = IginxStructuredUtils.mergeMountPath(detail.entity().getMountPath(), request.getSchema());
        String tablePath = IginxStructuredUtils.buildTablePath(schemaWithMount, request.getTable());
        String sql = "DELETE COLUMNS " + tablePath + ".*";
        try {
            structuredQueryHelper.executeSql(sql);
        } catch (Exception ex) {
            throw BizException.internal("删除表失败: " + ex.getMessage());
        }
    }

    private String toDisplayType(DataType type) {
        return type == null ? "BINARY" : type.name();
    }

    private DataType resolveStructuredType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return DataType.BINARY;
        }
        String normalized = rawType.trim().toUpperCase(Locale.ROOT);
        normalized = normalized.replaceAll("\\(.*\\)", "");
        if (normalized.contains("CHAR") || normalized.contains("TEXT") || normalized.contains("STRING")) {
            return DataType.BINARY;
        }
        if (normalized.contains("BOOL")) {
            return DataType.BOOLEAN;
        }
        if (normalized.contains("BIGINT") || normalized.contains("LONG")) {
            return DataType.LONG;
        }
        if (normalized.contains("INT")) {
            return DataType.INTEGER;
        }
        if (normalized.contains("FLOAT")) {
            return DataType.FLOAT;
        }
        if (normalized.contains("DOUBLE") || normalized.contains("DECIMAL") || normalized.contains("NUMERIC")
            || normalized.contains("REAL")) {
            return DataType.DOUBLE;
        }
        if (normalized.contains("DATE") || normalized.contains("TIME")) {
            return DataType.BINARY;
        }
        return DataType.BINARY;
    }

    private String buildCreateTableSql(String schema, String table, Map<String, DataType> columnTypes) {
        String tablePath = IginxStructuredUtils.buildTablePath(schema, table);
        StringBuilder builder = new StringBuilder();
        builder.append("INSERT INTO ").append(tablePath).append(" (KEY");
        for (String column : columnTypes.keySet()) {
            builder.append(", ").append(IginxStructuredUtils.buildColumnPath(schema, table, column));
        }
        builder.append(") VALUES (").append(IginxStructuredUtils.DUMMY_KEY);
        for (DataType type : columnTypes.values()) {
            builder.append(", ").append(IginxStructuredUtils.toSqlLiteral(defaultValue(type)));
        }
        builder.append(")");
        return builder.toString();
    }

    private Object defaultValue(DataType dataType) {
        return switch (dataType) {
            case BOOLEAN -> false;
            case INTEGER -> 0;
            case LONG -> 0L;
            case FLOAT -> 0.0f;
            case DOUBLE -> 0.0d;
            case BINARY -> new byte[0];
        };
    }

    private List<String> resolveDeleteTargets(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        String normalizedTarget = TimeSeriesPathUtils.normalizePath(path);
        List<Column> columns = iginxStorageWrapper.executeWithSession(session -> session.showColumns());
        List<String> targets = new ArrayList<>();
        for (Column column : columns) {
            if (column == null || column.getPath() == null) {
                continue;
            }
            String columnPath = TimeSeriesPathUtils.normalizePath(column.getPath());
            if (TimeSeriesPathUtils.startsWithPath(columnPath, normalizedTarget)) {
                targets.add(columnPath);
            }
        }
        return targets;
    }

    private boolean isReadOnlyDeleteWarning(BizException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.contains("Unable to delete data from read-only nodes");
    }
}
