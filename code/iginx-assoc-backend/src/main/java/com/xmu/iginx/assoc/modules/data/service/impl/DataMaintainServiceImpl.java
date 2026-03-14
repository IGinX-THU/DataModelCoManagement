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

/**
 * 数据维护服务实现，提供时序与结构化数据的增删改操作。
 */
@Service
@RequiredArgsConstructor
public class DataMaintainServiceImpl implements DataMaintainService {

    private final DataSourceAccessor dataSourceAccessor;
    private final IginxStorageWrapper iginxStorageWrapper;
    private final IginxStructuredQueryHelper structuredQueryHelper;

    /**
     * 删除时序数据。
     *
     * @param request 删除请求
     */
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
        // 统一挂载路径，避免路径不一致导致删除失败
        List<String> paths = TimeSeriesPathUtils.resolvePathsUnderMount(request.getPaths(), detail.entity().getMountPath(), true);
        // 将毫秒时间转换为纳秒，匹配 Iginx 删除接口
        long startNs = TimeParser.toNano(TimeParser.parseToMillis(request.getTimeRange().getStart(), null));
        long endNs = TimeParser.toNano(TimeParser.parseToMillis(request.getTimeRange().getEnd(), null));
        iginxStorageWrapper.executeWithSession(session -> {
            session.deleteDataInColumns(paths, startNs, endNs);
            return null;
        });
    }

    /**
     * 新增结构化数据行。
     *
     * @param request 新增请求
     */
    @Override
    public void createStructuredRow(StructuredRowCreateRequest request) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.POSTGRESQL);
        Map<String, Object> rawData = request.getData();
        if (rawData == null || rawData.isEmpty()) {
            throw BizException.badRequest("数据不能为空");
        }
        // 统一挂载路径，确保 schema 与真实表路径一致
        String schemaWithMount = IginxStructuredUtils.mergeMountPath(detail.entity().getMountPath(), request.getSchema());
        Map<String, DataType> columnTypes = requireColumnTypes(schemaWithMount, request.getTable());
        // 过滤内部字段，避免覆盖系统键
        Map<String, Object> data = normalizeData(rawData);
        if (data.isEmpty()) {
            throw BizException.badRequest("数据不能为空");
        }
        // 生成或解析内部主键
        long key = resolveCreateKey(rawData);
        String sql = buildInsertSql(schemaWithMount, request.getTable(), key, data, columnTypes);
        try {
            structuredQueryHelper.executeSql(sql);
        } catch (Exception ex) {
            throw BizException.internal("新增结构化数据失败: " + ex.getMessage());
        }
    }

    /**
     * 更新结构化数据行。
     *
     * @param request 更新请求
     */
    @Override
    public void updateStructuredRow(StructuredRowUpdateRequest request) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.POSTGRESQL);
        Map<String, Object> rawData = request.getData();
        if (rawData == null || rawData.isEmpty()) {
            throw BizException.badRequest("数据不能为空");
        }
        // 更新必须包含内部主键
        long key = resolveRequiredKey(rawData);
        String schemaWithMount = IginxStructuredUtils.mergeMountPath(detail.entity().getMountPath(), request.getSchema());
        Map<String, DataType> columnTypes = requireColumnTypes(schemaWithMount, request.getTable());
        // 过滤内部字段，避免更新主键列
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

    /**
     * 删除结构化数据行（通过内部键或哈希键定位）。
     *
     * @param request 删除请求
     */
    @Override
    public void deleteStructuredRow(StructuredRowDeleteRequest request) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.POSTGRESQL);
        Map<String, Object> keys = request.getKeys();
        long key = resolveDeleteKey(keys);
        String schemaWithMount = IginxStructuredUtils.mergeMountPath(detail.entity().getMountPath(), request.getSchema());
        Map<String, DataType> columnTypes = requireColumnTypes(schemaWithMount, request.getTable());
        List<String> columnPaths = new ArrayList<>();
        for (String column : columnTypes.keySet()) {
            if (column == null || column.isBlank()) {
                continue;
            }
            columnPaths.add(IginxStructuredUtils.buildColumnPath(schemaWithMount, request.getTable(), column));
        }
        if (columnPaths.isEmpty()) {
            throw BizException.badRequest("没有可删除的字段");
        }
        long endKey;
        try {
            // Iginx 删除接口是左闭右开区间，因此右边界需要 +1
            endKey = Math.addExact(key, 1L);
        } catch (ArithmeticException ex) {
            throw BizException.badRequest("内部键 _iginx_key 超出可用范围");
        }
        try {
            iginxStorageWrapper.executeWithSession(session -> {
                session.deleteDataInColumns(columnPaths, key, endKey);
                return null;
            });
        } catch (Exception ex) {
            throw BizException.internal("删除结构化数据失败: " + ex.getMessage());
        }
    }

    /**
     * 读取并校验表结构字段类型。
     *
     * @param schema schema 路径
     * @param table 表名
     * @return 列类型映射
     */
    private Map<String, DataType> requireColumnTypes(String schema, String table) {
        Map<String, DataType> columnTypes = structuredQueryHelper.loadColumnTypes(schema, table);
        if (columnTypes == null || columnTypes.isEmpty()) {
            throw BizException.badRequest("表结构不存在或无字段");
        }
        return columnTypes;
    }

    /**
     * 过滤内部字段，得到可写入的业务数据。
     *
     * @param data 原始数据
     * @return 规范化后的数据
     */
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
                // 跳过内部主键字段，避免业务侧覆盖
                continue;
            }
            normalized.put(entry.getKey(), entry.getValue());
        }
        return normalized;
    }

    /**
     * 校验列名是否存在于表结构中。
     *
     * @param columns 列名集合
     * @param columnTypes 列类型映射
     */
    private void validateColumns(Iterable<String> columns, Map<String, DataType> columnTypes) {
        for (String column : columns) {
            if (!columnTypes.containsKey(column)) {
                throw BizException.badRequest("字段不存在: " + column);
            }
        }
    }

    /**
     * 解析新增数据的内部键，未提供时生成随机键。
     *
     * @param data 原始数据
     * @return 内部键
     */
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

    /**
     * 解析更新所需的内部键。
     *
     * @param data 原始数据
     * @return 内部键
     */
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

    /**
     * 解析删除条件中的内部键，缺失时采用哈希键。
     *
     * @param keys 删除条件
     * @return 内部键
     */
    private long resolveDeleteKey(Map<String, Object> keys) {
        if (keys == null || keys.isEmpty()) {
            throw BizException.badRequest("删除条件不能为空");
        }
        Long internal = StructuredKeyGenerator.extractInternalKey(keys);
        // 未显式给出内部键时，用业务字段计算哈希键
        long key = internal != null ? internal : StructuredKeyGenerator.hashKey(keys);
        if (IginxStructuredUtils.isReservedKey(key)) {
            throw BizException.badRequest("内部键 _iginx_key 不合法");
        }
        if (key < 0) {
            throw BizException.badRequest("内部键 _iginx_key 不能为负数");
        }
        return key;
    }

    /**
     * 构建结构化写入 SQL。
     *
     * @param schema schema 路径
     * @param table 表名
     * @param key 内部键
     * @param data 写入数据
     * @param columnTypes 列类型映射
     * @return SQL 字符串
     */
    private String buildInsertSql(String schema, String table, long key,
                                 Map<String, Object> data,
                                 Map<String, DataType> columnTypes) {
        List<String> columns = new ArrayList<>(data.keySet());
        // 校验列名有效性
        validateColumns(columns, columnTypes);
        StringBuilder builder = new StringBuilder();
        builder.append("INSERT INTO ")
            .append(IginxStructuredUtils.buildTablePath(schema, table))
            .append(" (KEY");
        for (String column : columns) {
            builder.append(", ")
                .append(IginxStructuredUtils.buildInsertColumn(column));
        }
        builder.append(") VALUES (").append(key);
        for (String column : columns) {
            DataType type = columnTypes.getOrDefault(column, DataType.BINARY);
            // 按字段类型转换值，保证写入合法
            Object value = coerceValue(data.get(column), type);
            builder.append(", ").append(IginxStructuredUtils.toSqlLiteral(value));
        }
        builder.append(")");
        return builder.toString();
    }

    /**
     * 按列类型转换输入值。
     *
     * @param raw 原始值
     * @param type 列类型
     * @return 转换后的值
     */
    private Object coerceValue(Object raw, DataType type) {
        if (raw == null) {
            return null;
        }
        if (type == DataType.BINARY) {
            if (raw instanceof byte[] bytes) {
                return bytes;
            }
            // 非字节数组统一按 UTF-8 字节写入
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
        // 非数值统一走字符串解析
        return IginxDataTypeConverter.parseValue(String.valueOf(raw), type);
    }
}

