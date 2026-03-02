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

@Component
@RequiredArgsConstructor
public class IginxStructuredQueryHelper {

    private final IginxStorageWrapper iginxStorageWrapper;

    public QueryDataSet executeQuery(String sql, int fetchSize) {
        String normalized = normalizeSql(sql);
        return iginxStorageWrapper.executeWithSession(session -> session.executeQuery(normalized));
    }

    public void executeSql(String sql) {
        String normalized = normalizeSql(sql);
        iginxStorageWrapper.executeSql(normalized);
    }

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
        return readColumnTypes("SHOW COLUMNS;", tableSegments);
    }

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

    public List<Map<String, Object>> readAll(QueryDataSet dataSet) {
        return readAll(dataSet, null);
    }

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

    public List<Object[]> readRows(QueryDataSet dataSet) {
        List<Object[]> rows = new ArrayList<>();
        Object[] row;
        while ((row = nextRowQuietly(dataSet)) != null) {
            rows.add(row);
        }
        return rows;
    }

    private Object[] nextRowQuietly(QueryDataSet dataSet) {
        try {
            return dataSet.nextRow();
        } catch (Exception ex) {
            return null;
        }
    }

    private Object normalizeValue(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value;
    }

    private String toStringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

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

    private void closeQuietly(QueryDataSet dataSet) {
        if (dataSet == null) {
            return;
        }
        try {
            dataSet.close();
        } catch (Exception ignored) {
        }
    }

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
