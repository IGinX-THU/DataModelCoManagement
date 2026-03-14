package com.xmu.iginx.assoc.modules.data.util;

import cn.edu.tsinghua.iginx.session.QueryDataSet;
import cn.edu.tsinghua.iginx.thrift.DataType;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * IGinX 结构化查询辅助工具。
 */
@Component
@RequiredArgsConstructor
public class IginxStructuredQueryHelper {

    private final IginxStorageWrapper iginxStorageWrapper;

    /**
     * 执行查询 SQL，返回结果集。
     *
     * @param sql 查询 SQL
     * @param fetchSize 获取大小（保留参数以兼容调用方）
     * @return 查询结果集
     */
    public QueryDataSet executeQuery(String sql, int fetchSize) {
        String normalized = normalizeSql(sql);
        return iginxStorageWrapper.executeWithSession(session -> session.executeQuery(normalized));
    }

    /**
     * 执行非查询 SQL。
     *
     * @param sql SQL 语句
     */
    public void executeSql(String sql) {
        String normalized = normalizeSql(sql);
        iginxStorageWrapper.executeSql(normalized);
    }

    /**
     * 加载表的列类型映射。
     *
     * @param schema schema 路径
     * @param table 表名
     * @return 列类型映射
     */
    public Map<String, DataType> loadColumnTypes(String schema, String table) {
        String tablePath = IginxStructuredUtils.buildTablePath(schema, table);
        String sql = "SHOW COLUMNS " + tablePath + ".*;";
        Map<String, DataType> result = readColumnTypes(sql, null);
        if (result != null && !result.isEmpty()) {
            return result;
        }
        List<String> tableSegments = IginxStructuredUtils.splitPathSegments(tablePath);
        if (tableSegments.isEmpty()) {
            return result == null ? Map.of() : result;
        }
        // 回退到全量 SHOW COLUMNS，再根据表路径前缀过滤
        return readColumnTypes("SHOW COLUMNS;", tableSegments);
    }

    /**
     * 执行 SHOW COLUMNS 并解析列类型。
     *
     * @param sql 查询 SQL
     * @param requiredPrefix 必须匹配的路径前缀
     * @return 列类型映射
     */
    private Map<String, DataType> readColumnTypes(String sql, List<String> requiredPrefix) {
        QueryDataSet dataSet = executeQuery(sql, 1000);
        try {
            List<String> headers = dataSet.getColumnList();
            int pathIndex = indexOfIgnoreCase(headers, "Path");
            int typeIndex = indexOfIgnoreCase(headers, "DataType");
            if (typeIndex < 0) {
                typeIndex = indexOfIgnoreCase(headers, "Type");
            }
            if (pathIndex < 0 || typeIndex < 0) {
                return Map.of();
            }
            Map<String, DataType> result = new LinkedHashMap<>();
            Object[] row;
            while ((row = nextRowQuietly(dataSet)) != null) {
                if (row.length <= pathIndex || row.length <= typeIndex) {
                    continue;
                }
                String rawPath = toStringValue(row[pathIndex]);
                String rawType = toStringValue(row[typeIndex]);
                if (rawPath == null || rawPath.isBlank()) {
                    continue;
                }
                if (requiredPrefix != null && !requiredPrefix.isEmpty()) {
                    List<String> segments = IginxStructuredUtils.splitPathSegments(rawPath);
                    if (segments.size() <= requiredPrefix.size()) {
                        continue;
                    }
                    if (!IginxStructuredUtils.startsWithSegments(segments, requiredPrefix)) {
                        continue;
                    }
                }
                String columnName = IginxStructuredUtils.extractColumnName(rawPath);
                if (columnName == null || columnName.isBlank()) {
                    continue;
                }
                if (IginxStructuredUtils.isInternalKey(columnName)) {
                    continue;
                }
                DataType dataType = parseDataType(rawType);
                result.put(columnName, dataType);
            }
            return result;
        } finally {
            closeQuietly(dataSet);
        }
    }

    /**
     * 读取结果集为 Map 列表。
     *
     * @param dataSet 查询结果集
     * @return 记录列表
     */
    public List<Map<String, Object>> readAll(QueryDataSet dataSet) {
        return readAll(dataSet, null);
    }

    /**
     * 读取结果集为 Map 列表（支持自定义表头）。
     *
     * @param dataSet 查询结果集
     * @param headerOverride 自定义表头
     * @return 记录列表
     */
    public List<Map<String, Object>> readAll(QueryDataSet dataSet, List<String> headerOverride) {
        List<Map<String, Object>> records = new ArrayList<>();
        List<String> rawHeaders = dataSet.getColumnList();
        List<String> headers = headerOverride;
        if (headers == null || headers.isEmpty() || headers.size() != rawHeaders.size()) {
            headers = rawHeaders;
        }
        Object[] row;
        while ((row = nextRowQuietly(dataSet)) != null) {
            Map<String, Object> record = new LinkedHashMap<>();
            int headerCount = headers.size();
            for (int i = 0; i < headerCount; i++) {
                Object value = i < row.length ? row[i] : null;
                record.put(headers.get(i), normalizeValue(value));
            }
            records.add(record);
        }
        return records;
    }

    /**
     * 读取结果集为行数组列表。
     *
     * @param dataSet 查询结果集
     * @return 行列表
     */
    public List<Object[]> readRows(QueryDataSet dataSet) {
        List<Object[]> rows = new ArrayList<>();
        Object[] row;
        while ((row = nextRowQuietly(dataSet)) != null) {
            rows.add(row);
        }
        return rows;
    }

    /**
     * 安静读取下一行。
     *
     * @param dataSet 查询结果集
     * @return 下一行或 null
     */
    private Object[] nextRowQuietly(QueryDataSet dataSet) {
        try {
            return dataSet.nextRow();
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 规范化结果值（处理二进制字段）。
     *
     * @param value 原始值
     * @return 规范化值
     */
    private Object normalizeValue(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value;
    }

    /**
     * 将结果值转换为字符串。
     *
     * @param value 原始值
     * @return 字符串值
     */
    private String toStringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    /**
     * 忽略大小写查找列名索引。
     *
     * @param headers 表头
     * @param target 目标列名
     * @return 索引，找不到返回 -1
     */
    private int indexOfIgnoreCase(List<String> headers, String target) {
        if (headers == null || target == null) {
            return -1;
        }
        for (int i = 0; i < headers.size(); i++) {
            if (target.equalsIgnoreCase(headers.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 解析字段类型字符串。
     *
     * @param rawType 类型字符串
     * @return DataType
     */
    private DataType parseDataType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return DataType.BINARY;
        }
        try {
            return DataType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return DataType.BINARY;
        }
    }

    /**
     * 安静关闭结果集。
     *
     * @param dataSet 查询结果集
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
     * 规范化 SQL，确保以分号结尾。
     *
     * @param sql SQL 语句
     * @return 规范化 SQL
     */
    private String normalizeSql(String sql) {
        if (sql == null) {
            return null;
        }
        String trimmed = sql.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return trimmed.endsWith(";") ? trimmed : trimmed + ";";
    }
}
