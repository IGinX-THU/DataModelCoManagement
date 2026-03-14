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

/**
 * 结构管理服务实现，提供时序与结构化数据的结构维护能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StructureServiceImpl implements StructureService {

    private final DataSourceAccessor dataSourceAccessor;
    private final IginxStorageWrapper iginxStorageWrapper;
    private final IginxStructuredQueryHelper structuredQueryHelper;

    /**
     * 查询结构化表字段列表。
     *
     * @param sourceId 数据源 ID
     * @param schema schema 名称
     * @param table 表名
     * @return 字段列表
     */
    @Override
    public List<TableColumnVO> listTableColumns(Long sourceId, String schema, String table) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(sourceId, DataSourceType.POSTGRESQL);
        try {
            // 统一挂载路径，确保 schema 与真实路径一致
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

    /**
     * 创建时序存储组（通过插入 __init__ 测点实现）。
     *
     * @param request 存储组请求
     */
    @Override
    public void createStorageGroup(StorageGroupRequest request) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.INFLUXDB, DataSourceType.IOTDB);
        String path = TimeSeriesPathUtils.resolvePathUnderMount(request.getPath(), detail.entity().getMountPath(), true);
        MeasurementRequest measurementRequest = new MeasurementRequest();
        measurementRequest.setSourceId(request.getSourceId());
        measurementRequest.setPath(TimeSeriesPathUtils.joinPath(path, "__init__"));
        measurementRequest.setDataType("DOUBLE");
        // 通过插入初始化测点来创建存储组
        createMeasurement(measurementRequest);
    }

    /**
     * 删除时序存储组（删除其下所有测点）。
     *
     * @param request 存储组请求
     */
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
                log.warn("删除存储组包含只读分片，已清理可写数据：{}", path);
                return;
            }
            throw ex;
        }
    }

    /**
     * 创建测点（插入一条默认值记录）。
     *
     * @param request 测点请求
     */
    @Override
    public void createMeasurement(MeasurementRequest request) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.INFLUXDB, DataSourceType.IOTDB);
        String path = TimeSeriesPathUtils.resolvePathUnderMount(request.getPath(), detail.entity().getMountPath(), false);
        DataType dataType = IginxDataTypeConverter.parseType(request.getDataType());
        // 以当前时间写入默认值，用于创建测点
        long timestamp = System.currentTimeMillis() * 1_000_000;
        Object value = defaultValue(dataType);
        iginxStorageWrapper.executeWithSession(session -> {
            session.insertRowRecords(List.of(path), new long[]{timestamp}, new Object[]{new Object[]{value}},
                List.of(dataType), null);
            return null;
        });
    }

    /**
     * 删除测点。
     *
     * @param request 测点请求
     */
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
                log.warn("删除测点包含只读分片，已清理可写数据：{}", path);
                return;
            }
            throw ex;
        }
    }

    /**
     * 创建结构化表（通过插入 DUMMY_KEY 行完成建表）。
     *
     * @param request 建表请求
     */
    @Override
    public void createTable(TableCreateRequest request) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.POSTGRESQL);
        if (request.getColumns() == null || request.getColumns().isEmpty()) {
            throw BizException.badRequest("建表字段不能为空");
        }
        // 统一挂载路径，保证 schema 与真实表路径一致
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
            // 将用户输入的类型转换为 Iginx 兼容类型
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

    /**
     * 删除结构化表。
     *
     * @param request 删表请求
     */
    @Override
    public void dropTable(TableDropRequest request) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.POSTGRESQL);
        String schemaWithMount = IginxStructuredUtils.mergeMountPath(detail.entity().getMountPath(), request.getSchema());
        String tablePath = IginxStructuredUtils.buildTablePath(schemaWithMount, request.getTable());
        // Iginx 通过 DELETE COLUMNS 删除整表
        String sql = "DELETE COLUMNS " + tablePath + ".*";
        try {
            structuredQueryHelper.executeSql(sql);
        } catch (Exception ex) {
            throw BizException.internal("删除表失败: " + ex.getMessage());
        }
    }

    /**
     * 将内部类型转换为展示类型。
     *
     * @param type 数据类型
     * @return 展示类型
     */
    private String toDisplayType(DataType type) {
        return type == null ? "BINARY" : type.name();
    }

    /**
     * 将用户输入的字段类型映射为 Iginx 类型。
     *
     * @param rawType 原始类型字符串
     * @return Iginx 类型
     */
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

    /**
     * 构建建表 SQL（通过插入 DUMMY_KEY 记录）。
     *
     * @param schema schema 路径
     * @param table 表名
     * @param columnTypes 列类型
     * @return SQL 字符串
     */
    private String buildCreateTableSql(String schema, String table, Map<String, DataType> columnTypes) {
        String tablePath = IginxStructuredUtils.buildTablePath(schema, table);
        StringBuilder builder = new StringBuilder();
        builder.append("INSERT INTO ").append(tablePath).append(" (KEY");
        for (String column : columnTypes.keySet()) {
            builder.append(", ").append(IginxStructuredUtils.buildInsertColumn(column));
        }
        builder.append(") VALUES (").append(IginxStructuredUtils.DUMMY_KEY);
        for (DataType type : columnTypes.values()) {
            builder.append(", ").append(IginxStructuredUtils.toSqlLiteral(defaultValue(type)));
        }
        builder.append(")");
        return builder.toString();
    }

    /**
     * 生成默认值用于建表写入。
     *
     * @param dataType 数据类型
     * @return 默认值
     */
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

    /**
     * 解析需要删除的测点路径列表。
     *
     * @param path 目标路径
     * @return 测点路径列表
     */
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

    /**
     * 判断删除时是否命中只读分片警告。
     *
     * @param ex 业务异常
     * @return 是否只读警告
     */
    private boolean isReadOnlyDeleteWarning(BizException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.contains("Unable to delete data from read-only nodes");
    }
}
