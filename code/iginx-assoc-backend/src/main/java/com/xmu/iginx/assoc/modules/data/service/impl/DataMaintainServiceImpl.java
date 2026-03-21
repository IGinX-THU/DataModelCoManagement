package com.xmu.iginx.assoc.modules.data.service.impl;

import cn.edu.tsinghua.iginx.session.SessionExecuteSqlResult;
import cn.edu.tsinghua.iginx.thrift.DataType;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.dto.DataColumnsDeleteRequest;
import com.xmu.iginx.assoc.modules.data.dto.StructuredRowCreateRequest;
import com.xmu.iginx.assoc.modules.data.dto.StructuredRowDeleteRequest;
import com.xmu.iginx.assoc.modules.data.dto.StructuredRowUpdateRequest;
import com.xmu.iginx.assoc.modules.data.service.DataMaintainService;
import com.xmu.iginx.assoc.modules.data.util.DataPrefixRules;
import com.xmu.iginx.assoc.modules.data.util.IginxDataTypeConverter;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredQueryHelper;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredUtils;
import com.xmu.iginx.assoc.modules.data.util.TimeSeriesPathUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * 数据维护服务实现，提供时序与结构化数据的增删改操作。
 */
@Service
@RequiredArgsConstructor
public class DataMaintainServiceImpl implements DataMaintainService {

    private static final Pattern PATH_SEGMENT_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * 结构化“自动生成 KEY”序列。
     * <p>
     * 使用原子递增保证并发下不会重复，初始值以当前毫秒时间戳作为起点。
     * </p>
     */
    private static final AtomicLong GENERATED_KEY_SEQUENCE = new AtomicLong(System.currentTimeMillis());
    private final IginxStorageWrapper iginxStorageWrapper;
    private final IginxStructuredQueryHelper structuredQueryHelper;

    /**
     * 新增结构化单行数据。
     * <p>
     * 实现策略：优先使用 Java SDK 的 {@code insertRowRecords} 写入。
     * 对应用户手册 v0.8.0 第 90~92 页（9.1/9.2 节）对行式写入 API 的定义。
     * </p>
     *
     * @param request 新增请求
     */
    @Override
    public void createStructuredRow(StructuredRowCreateRequest request) {
        if (request == null) {
            throw BizException.badRequest("新增请求不能为空");
        }
        String tablePath = normalizeStructuredTablePath(request.getPath());
        Map<String, Object> writableData = normalizeWritableData(request.getData());
        long key = resolveCreateKeyAndStrip(writableData);
        if (writableData.isEmpty()) {
            throw BizException.badRequest("新增数据不能为空，至少包含一个业务字段");
        }

        Map<String, DataType> columnTypes = loadColumnTypes(tablePath);
        writeStructuredRowBySdk(tablePath, key, writableData, columnTypes);
    }

    /**
     * 更新结构化单行数据。
     * <p>
     * 实现策略：使用 Java SDK 封装“重写 / 删后写”两种模式。
     * </p>
     * <p>1. 请求字段覆盖全部业务列时，先删后写（整行重建）；</p>
     * <p>2. 仅传入部分字段时，直接重写（避免误删未传字段）。</p>
     *
     * @param request 更新请求
     */
    @Override
    public void updateStructuredRow(StructuredRowUpdateRequest request) {
        if (request == null) {
            throw BizException.badRequest("更新请求不能为空");
        }
        String tablePath = normalizeStructuredTablePath(request.getPath());
        Map<String, Object> writableData = normalizeWritableData(request.getData());
        long key = resolveRequiredKeyAndStrip(writableData, "更新请求缺少 KEY/_iginx_key");
        if (writableData.isEmpty()) {
            throw BizException.badRequest("更新数据不能为空，至少包含一个业务字段");
        }

        Map<String, DataType> columnTypes = loadColumnTypes(tablePath);
        if (shouldDeleteBeforeRewrite(writableData, columnTypes)) {
            deleteStructuredRowBySdk(tablePath, key, columnTypes.keySet());
        }
        writeStructuredRowBySdk(tablePath, key, writableData, columnTypes);
    }

    /**
     * 删除结构化单行数据。
     * <p>
     * 实现策略：优先使用 Java SDK 的 {@code deleteDataInColumns}。
     * 对应用户手册 v0.8.0 第 90~92 页（9.1/9.2 节）删除 API 说明。
     * </p>
     * <p>
     * 说明：SDK 删除是范围语义。这里通过 [key, key + 1) 表示“单行删除”。
     * 若 key 为 Long.MAX_VALUE，无法安全 +1，会回退到 SQL 精确删除。
     * </p>
     *
     * @param request 删除请求
     */
    @Override
    public void deleteStructuredRow(StructuredRowDeleteRequest request) {
        if (request == null) {
            throw BizException.badRequest("删除请求不能为空");
        }
        String tablePath = normalizeStructuredTablePath(request.getPath());
        Map<String, Object> keyData = normalizeWritableData(request.getKeys());
        long key = resolveRequiredKeyAndStrip(keyData, "删除请求缺少 KEY/_iginx_key");

        Map<String, DataType> columnTypes = loadColumnTypes(tablePath);
        if (columnTypes.isEmpty()) {
            // 元数据无法获取时，无法构造 SDK 删除列路径，回退 SQL 保障可用性。
            SessionExecuteSqlResult result = iginxStorageWrapper.executeSql(
                buildDeleteStructuredRowSql(buildDeleteColumnsTarget(tablePath), key)
            );
            validateIginxSqlResult(result);
            return;
        }
        deleteStructuredRowBySdk(tablePath, key, columnTypes.keySet());
    }

    /**
     * 删除路径下的全部数据（DELETE COLUMNS）。
     *
     * @param request 删除请求
     */
    @Override
    public void deleteColumns(DataColumnsDeleteRequest request) {
        if (request == null) {
            throw BizException.badRequest("路径不能为空");
        }
        String normalized = TimeSeriesPathUtils.normalizePath(request.getPath());
        if (!StringUtils.hasText(normalized)) {
            throw BizException.badRequest("路径不能为空");
        }
        if (containsIllegalChars(normalized)) {
            throw BizException.badRequest("路径包含非法字符");
        }
        if (!DataPrefixRules.startsWithPrefix(normalized, DataPrefixRules.TS_PREFIX)
            && !DataPrefixRules.startsWithPrefix(normalized, DataPrefixRules.RT_PREFIX)
            && !DataPrefixRules.startsWithPrefix(normalized, DataPrefixRules.MODEL_PREFIX)) {
            throw BizException.badRequest("路径前缀必须是 ts / rt / models");
        }
        boolean includeChildren = Boolean.TRUE.equals(request.getIncludeChildren());
        String target = normalized;
        if (includeChildren) {
            if (!normalized.endsWith(".*")) {
                target = normalized + ".*";
            }
        } else if (normalized.endsWith(".*")) {
            target = normalized.substring(0, normalized.length() - 2);
        }
        String quotedTarget = buildDeleteColumnsTarget(target);
        SessionExecuteSqlResult result = iginxStorageWrapper.executeSql("DELETE COLUMNS " + quotedTarget + ";");
        validateIginxSqlResult(result);
    }

    /**
     * 构建删除结构化单行的 SQL。
     *
     * @param tablePath 表路径
     * @param key 内部键
     * @return DELETE SQL
     */
    private String buildDeleteStructuredRowSql(String tablePath, long key) {
        if (!StringUtils.hasText(tablePath)) {
            throw BizException.badRequest("表路径不能为空");
        }
        // 按用户手册 3.2.2 约束，删除条件仅使用 KEY 范围表达式。
        return "DELETE FROM " + tablePath + ".* WHERE KEY >= " + key + " AND KEY <= " + key + ";";
    }

    /**
     * 规范化结构化表路径。
     *
     * @param path 请求路径
     * @return 可用于 SDK 的表路径
     */
    private String normalizeStructuredTablePath(String path) {
        String normalized = TimeSeriesPathUtils.normalizePath(path);
        if (normalized.startsWith("root.")) {
            normalized = normalized.substring("root.".length());
        }
        if (!StringUtils.hasText(normalized)) {
            throw BizException.badRequest("路径不能为空");
        }
        if (containsIllegalChars(normalized)) {
            throw BizException.badRequest("路径包含非法字符");
        }
        if (!DataPrefixRules.startsWithPrefix(normalized, DataPrefixRules.RT_PREFIX)) {
            throw BizException.badRequest("结构化行操作路径必须以 rt 开头");
        }
        String cleanPath = normalized.endsWith(".*")
            ? normalized.substring(0, normalized.length() - 2)
            : normalized;
        if (!StringUtils.hasText(cleanPath)) {
            throw BizException.badRequest("结构化表路径不能为空");
        }
        return cleanPath;
    }

    /**
     * 校验 IGinX SQL 执行结果，统一处理解析失败。
     *
     * @param result 执行结果
     */
    private void validateIginxSqlResult(SessionExecuteSqlResult result) {
        if (result != null && StringUtils.hasText(result.getParseErrorMsg())) {
            throw BizException.badRequest(result.getParseErrorMsg().trim());
        }
    }

    /**
     * 构建 DELETE COLUMNS 路径目标，按段补充反引号转义。
     *
     * @param pathWithWildcard 路径（可带 .*）
     * @return 适用于 SQL 的路径
     */
    private String buildDeleteColumnsTarget(String pathWithWildcard) {
        String normalized = TimeSeriesPathUtils.normalizePath(pathWithWildcard);
        boolean wildcard = normalized.endsWith(".*");
        String basePath = wildcard ? normalized.substring(0, normalized.length() - 2) : normalized;
        List<String> segments = IginxStructuredUtils.splitPathSegments(basePath);
        if (segments.isEmpty()) {
            return wildcard ? "*" : "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < segments.size(); i += 1) {
            String segment = segments.get(i);
            if (segment == null || segment.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('.');
            }
            builder.append(quotePathSegment(segment));
        }
        if (wildcard) {
            builder.append(".*");
        }
        return builder.toString();
    }

    /**
     * 路径段转义：普通标识符直接输出，复杂段使用反引号。
     *
     * @param segment 路径段
     * @return 转义后的路径段
     */
    private String quotePathSegment(String segment) {
        if (PATH_SEGMENT_PATTERN.matcher(segment).matches()) {
            return segment;
        }
        String escaped = segment.replace("\\", "\\\\").replace("`", "\\`");
        return "`" + escaped + "`";
    }

    /**
     * 判断路径或字段名是否含有明显非法字符。
     *
     * @param path 路径或字段
     * @return 是否非法
     */
    private boolean containsIllegalChars(String path) {
        return path.contains(";")
            || path.contains(" ")
            || path.contains("\t")
            || path.contains("\n")
            || path.contains("\r");
    }

    /**
     * 将请求中的业务数据规范化为可写 Map。
     *
     * @param rawData 原始数据
     * @return 规范化后数据
     */
    private Map<String, Object> normalizeWritableData(Map<String, Object> rawData) {
        if (rawData == null || rawData.isEmpty()) {
            throw BizException.badRequest("数据不能为空");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawData.entrySet()) {
            String column = normalizeColumnName(entry.getKey());
            if (!StringUtils.hasText(column)) {
                continue;
            }
            if (containsIllegalChars(column)) {
                throw BizException.badRequest("字段名包含非法字符: " + column);
            }
            normalized.put(column, entry.getValue());
        }
        return normalized;
    }

    /**
     * 解析“新增场景”的 KEY，并移除 KEY 字段。
     *
     * @param data 可写数据
     * @return 行键
     */
    private long resolveCreateKeyAndStrip(Map<String, Object> data) {
        Long explicitKey = extractAndStripInternalKey(data);
        if (explicitKey != null) {
            return explicitKey;
        }
        return GENERATED_KEY_SEQUENCE.updateAndGet(previous -> {
            long now = System.currentTimeMillis();
            return Math.max(previous + 1, now);
        });
    }

    /**
     * 解析“必须携带 KEY”的场景（更新、删除），并移除 KEY 字段。
     *
     * @param data 可写数据
     * @param message 缺失 KEY 时错误信息
     * @return 行键
     */
    private long resolveRequiredKeyAndStrip(Map<String, Object> data, String message) {
        Long key = extractAndStripInternalKey(data);
        if (key == null) {
            throw BizException.badRequest(message);
        }
        return key;
    }

    /**
     * 提取并移除 KEY / _iginx_key 字段。
     *
     * @param data 可写数据
     * @return key（可为空）
     */
    private Long extractAndStripInternalKey(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        Long key = null;
        List<String> aliases = new ArrayList<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!isKeyAlias(entry.getKey())) {
                continue;
            }
            aliases.add(entry.getKey());
            if (key == null) {
                key = parseKey(entry.getValue());
            }
        }
        for (String alias : aliases) {
            data.remove(alias);
        }
        return key;
    }

    /**
     * 判断字段名是否代表 KEY。
     *
     * @param column 字段名
     * @return 是否 KEY 字段
     */
    private boolean isKeyAlias(String column) {
        if (!StringUtils.hasText(column)) {
            return false;
        }
        String normalized = column.trim();
        return "KEY".equalsIgnoreCase(normalized);
    }

    /**
     * 解析 KEY 值。
     *
     * @param raw 原始值
     * @return key（可为空）
     */
    private Long parseKey(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof byte[] bytes) {
            return parseKeyText(new String(bytes, StandardCharsets.UTF_8));
        }
        return parseKeyText(String.valueOf(raw));
    }

    /**
     * 解析文本 KEY。
     *
     * @param text 文本
     * @return key（可为空）
     */
    private Long parseKeyText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (Exception ex) {
            throw BizException.badRequest("KEY 必须是 long 类型");
        }
    }

    /**
     * 判断更新是否应走“删后写”。
     *
     * @param writableData 待写字段
     * @param columnTypes 表结构字段
     * @return 是否先删后写
     */
    private boolean shouldDeleteBeforeRewrite(Map<String, Object> writableData, Map<String, DataType> columnTypes) {
        if (columnTypes == null || columnTypes.isEmpty()) {
            return false;
        }
        if (writableData == null || writableData.isEmpty()) {
            return false;
        }
        if (writableData.size() < columnTypes.size()) {
            return false;
        }
        for (String column : columnTypes.keySet()) {
            if (!containsColumnIgnoreCase(writableData, column)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 使用 Java SDK 执行结构化单行写入。
     *
     * @param tablePath 表路径
     * @param key 行键
     * @param data 待写数据
     * @param columnTypes 列类型映射
     */
    private void writeStructuredRowBySdk(String tablePath,
                                         long key,
                                         Map<String, Object> data,
                                         Map<String, DataType> columnTypes) {
        List<String> columns = new ArrayList<>(data.keySet());
        validateColumns(columns);
        List<String> columnPaths = buildColumnPaths(tablePath, columns);

        List<DataType> types = new ArrayList<>();
        Object[] rowValues = new Object[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            DataType type = resolveColumnType(column, columnTypes);
            types.add(type);
            rowValues[i] = coerceValue(data.get(column), type);
        }

        long[] keys = new long[]{key};
        Object[] values = new Object[]{rowValues};
        iginxStorageWrapper.executeWithSession(session -> {
            session.insertRowRecords(columnPaths, keys, values, types, null);
            return null;
        });
    }

    /**
     * 使用 Java SDK 删除结构化单行。
     *
     * @param tablePath 表路径
     * @param key 行键
     * @param columns 列集合
     */
    private void deleteStructuredRowBySdk(String tablePath, long key, Iterable<String> columns) {
        if (key == Long.MAX_VALUE) {
            // SDK 删除使用 [start, end)；MAX_VALUE 无法 +1，改走 SQL 精确删除。
            SessionExecuteSqlResult result = iginxStorageWrapper.executeSql(
                buildDeleteStructuredRowSql(buildDeleteColumnsTarget(tablePath), key)
            );
            validateIginxSqlResult(result);
            return;
        }
        List<String> columnPaths = buildColumnPaths(tablePath, columns);
        if (columnPaths.isEmpty()) {
            throw BizException.badRequest("结构化表无可删除字段");
        }
        long endExclusive = key + 1;
        iginxStorageWrapper.executeWithSession(session -> {
            session.deleteDataInColumns(columnPaths, key, endExclusive);
            return null;
        });
    }

    /**
     * 构建 SDK 需要的“完整列路径”。
     *
     * @param tablePath 表路径
     * @param columns 列集合
     * @return 列路径列表
     */
    private List<String> buildColumnPaths(String tablePath, Iterable<String> columns) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(tablePath) || columns == null) {
            return result;
        }
        for (String rawColumn : columns) {
            String column = normalizeColumnName(rawColumn);
            if (!StringUtils.hasText(column)) {
                continue;
            }
            if (isKeyAlias(column)) {
                continue;
            }
            result.add(TimeSeriesPathUtils.joinPath(tablePath, column));
        }
        return result;
    }

    /**
     * 按列名（忽略大小写）解析列类型。
     *
     * @param column 列名
     * @param columnTypes 类型映射
     * @return DataType
     */
    private DataType resolveColumnType(String column, Map<String, DataType> columnTypes) {
        if (columnTypes == null || columnTypes.isEmpty()) {
            return DataType.BINARY;
        }
        DataType exact = columnTypes.get(column);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, DataType> entry : columnTypes.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(column)) {
                return entry.getValue() == null ? DataType.BINARY : entry.getValue();
            }
        }
        return DataType.BINARY;
    }

    /**
     * 加载表字段类型映射。
     *
     * @param tablePath 表路径
     * @return 字段类型映射
     */
    private Map<String, DataType> loadColumnTypes(String tablePath) {
        Map<String, DataType> loaded = structuredQueryHelper.loadColumnTypesByTablePath(tablePath);
        if (loaded == null || loaded.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(loaded);
    }

    /**
     * 校验列名合法性。
     *
     * @param columns 列名列表
     */
    private void validateColumns(List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            throw BizException.badRequest("数据不能为空，至少包含一个业务字段");
        }
        for (String column : columns) {
            String normalized = normalizeColumnName(column);
            if (!StringUtils.hasText(normalized)) {
                throw BizException.badRequest("字段名不能为空");
            }
            if (isKeyAlias(normalized)) {
                throw BizException.badRequest("业务字段中不允许包含 KEY/_iginx_key");
            }
            if (containsIllegalChars(normalized)) {
                throw BizException.badRequest("字段名包含非法字符: " + normalized);
            }
            List<String> segments = IginxStructuredUtils.splitPathSegments(normalized);
            if (segments.isEmpty()) {
                throw BizException.badRequest("字段名不能为空");
            }
            for (String segment : segments) {
                if (!StringUtils.hasText(segment)) {
                    throw BizException.badRequest("字段名格式错误: " + normalized);
                }
            }
        }
    }

    /**
     * 规范化字段名（去空白和尾部点）。
     *
     * @param rawColumn 原始字段名
     * @return 规范化字段名
     */
    private String normalizeColumnName(String rawColumn) {
        if (rawColumn == null) {
            return "";
        }
        String normalized = rawColumn.trim();
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    /**
     * 忽略大小写判断 Map 是否包含某列。
     *
     * @param data 数据
     * @param column 列名
     * @return 是否包含
     */
    private boolean containsColumnIgnoreCase(Map<String, Object> data, String column) {
        if (data == null || data.isEmpty() || !StringUtils.hasText(column)) {
            return false;
        }
        for (String key : data.keySet()) {
            if (key != null && key.equalsIgnoreCase(column)) {
                return true;
            }
        }
        return false;
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
            // 非字节数组统一转 UTF-8 字节，与查询端字符串解码策略保持一致。
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
        // 其余类型统一按字符串解析，避免多处分散的转换逻辑不一致。
        return IginxDataTypeConverter.parseValue(String.valueOf(raw), type);
    }
}
