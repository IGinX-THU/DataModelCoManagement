package com.xmu.iginx.assoc.modules.data.util;

import cn.edu.tsinghua.iginx.thrift.DataType;

import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class IginxStructuredUtils {

    public static final String INTERNAL_KEY = "_iginx_key";
    // 使用最大值会触发 IGinX 路由异常，保留一个安全值作为占位 Key
    public static final long DUMMY_KEY = Long.MAX_VALUE - 1;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private IginxStructuredUtils() {
    }

    public static boolean isInternalKey(String column) {
        if (column == null) {
            return false;
        }
        String trimmed = column.trim();
        return INTERNAL_KEY.equalsIgnoreCase(trimmed)
            || trimmed.endsWith(".RELATIONAL+KEY")
            || "RELATIONAL+KEY".equalsIgnoreCase(trimmed);
    }

    public static boolean isReservedKey(long key) {
        return key == DUMMY_KEY || key == Long.MAX_VALUE;
    }

    public static String quoteIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        String trimmed = identifier.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.matches("[A-Za-z0-9_]+")) {
            return trimmed;
        }
        String escaped = trimmed.replace("\\", "\\\\").replace("`", "\\`");
        return "`" + escaped + "`";
    }

    public static String buildTablePath(String schema, String table) {
        List<String> schemaSegments = splitPathSegments(schema);
        List<String> tableSegments = splitPathSegments(table);
        List<String> segments = new ArrayList<>();
        if (!schemaSegments.isEmpty()) {
            if (startsWithSegments(tableSegments, schemaSegments)) {
                segments.addAll(tableSegments);
            } else {
                segments.addAll(schemaSegments);
                segments.addAll(tableSegments);
            }
        } else {
            segments.addAll(tableSegments);
        }
        if (segments.isEmpty()) {
            return "";
        }
        return segments.stream().map(IginxStructuredUtils::quoteIdentifier).collect(java.util.stream.Collectors.joining("."));
    }

    public static String mergeMountPath(String mountPath, String schema) {
        List<String> mountSegments = splitPathSegments(mountPath);
        if (mountSegments.isEmpty()) {
            return schema == null ? "" : schema;
        }
        if (schema == null || schema.isBlank()) {
            return String.join(".", mountSegments);
        }
        List<String> schemaSegments = splitPathSegments(schema);
        if (startsWithSegments(schemaSegments, mountSegments)) {
            return schema;
        }
        List<String> combined = new ArrayList<>(mountSegments);
        combined.addAll(schemaSegments);
        return String.join(".", combined);
    }

    public static String buildTablePathWithMount(String mountPath, String schema, String table) {
        String mergedSchema = mergeMountPath(mountPath, schema);
        return buildTablePath(mergedSchema, table);
    }

    public static String buildColumnPathWithMount(String mountPath, String schema, String table, String column) {
        return buildTablePathWithMount(mountPath, schema, table) + "." + quoteIdentifier(column);
    }

    public static String buildColumnPath(String schema, String table, String column) {
        return buildTablePath(schema, table) + "." + quoteIdentifier(column);
    }

    public static String buildInsertColumn(String column) {
        if (column == null || column.isBlank()) {
            return "";
        }
        List<String> segments = splitPathSegments(column);
        if (segments.isEmpty()) {
            return "";
        }
        return segments.stream()
            .map(IginxStructuredUtils::quoteIdentifier)
            .collect(java.util.stream.Collectors.joining("."));
    }

    public static String buildSelectList(String schema, String table, List<String> columns, boolean includeKey) {
        return buildSelectList(schema, table, columns, includeKey, true);
    }

    public static String buildSelectList(String schema,
                                         String table,
                                         List<String> columns,
                                         boolean includeKey,
                                         boolean useAlias) {
        List<String> parts = new ArrayList<>();
        if (includeKey) {
            if (useAlias) {
                parts.add("KEY AS " + quoteIdentifier(INTERNAL_KEY));
            } else {
                parts.add("KEY");
            }
        }
        if (columns != null) {
            for (String column : columns) {
                if (column == null || column.isBlank()) {
                    continue;
                }
                String path = buildColumnPath(schema, table, column);
                if (useAlias) {
                    parts.add(path + " AS " + quoteIdentifier(column));
                } else {
                    parts.add(path);
                }
            }
        }
        if (parts.isEmpty()) {
            return "*";
        }
        return String.join(", ", parts);
    }

    public static String renderSqlWithParams(String sql, List<Object> params) {
        if (params == null || params.isEmpty() || sql == null || sql.isBlank()) {
            return sql;
        }
        StringBuilder builder = new StringBuilder();
        int paramIndex = 0;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '?' && paramIndex < params.size()) {
                builder.append(toSqlLiteral(params.get(paramIndex++)));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    public static String toSqlLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number number) {
            return number.toString();
        }
        if (value instanceof Boolean bool) {
            return bool ? "TRUE" : "FALSE";
        }
        if (value instanceof byte[] bytes) {
            return quoteString(new String(bytes, StandardCharsets.UTF_8));
        }
        if (value instanceof java.sql.Timestamp ts) {
            return quoteString(DATE_TIME_FORMATTER.format(ts.toLocalDateTime()));
        }
        if (value instanceof java.sql.Date date) {
            return quoteString(DATE_FORMATTER.format(date.toLocalDate()));
        }
        if (value instanceof java.sql.Time time) {
            return quoteString(TIME_FORMATTER.format(time.toLocalTime()));
        }
        if (value instanceof LocalDateTime dateTime) {
            return quoteString(DATE_TIME_FORMATTER.format(dateTime));
        }
        if (value instanceof LocalDate date) {
            return quoteString(DATE_FORMATTER.format(date));
        }
        if (value instanceof LocalTime time) {
            return quoteString(TIME_FORMATTER.format(time));
        }
        return quoteString(value.toString());
    }

    private static String quoteString(String value) {
        String escaped = value == null ? "" : value.replace("\\", "\\\\").replace("'", "\\'");
        return "'" + escaped + "'";
    }

    public static Map<String, Integer> mapIginxTypesToSqlTypes(Map<String, DataType> columnTypes) {
        if (columnTypes == null || columnTypes.isEmpty()) {
            return Map.of();
        }
        java.util.Map<String, Integer> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, DataType> entry : columnTypes.entrySet()) {
            result.put(entry.getKey(), toSqlType(entry.getValue()));
        }
        return result;
    }

    public static int toSqlType(DataType type) {
        if (type == null) {
            return Types.VARCHAR;
        }
        return switch (type) {
            case BOOLEAN -> Types.BOOLEAN;
            case INTEGER -> Types.INTEGER;
            case LONG -> Types.BIGINT;
            case FLOAT -> Types.FLOAT;
            case DOUBLE -> Types.DOUBLE;
            case BINARY -> Types.VARCHAR;
        };
    }

    public static List<String> normalizeStructuredHeaders(List<String> headers) {
        if (headers == null || headers.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String header : headers) {
            if (header == null || header.isBlank()) {
                result.add("");
                continue;
            }
            String trimmed = header.trim();
            if ("KEY".equalsIgnoreCase(trimmed) || isInternalKey(trimmed)) {
                result.add(INTERNAL_KEY);
                continue;
            }
            result.add(extractColumnName(trimmed));
        }
        return result;
    }

    public static String extractColumnName(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path;
        int tagIndex = normalized.indexOf('{');
        if (tagIndex >= 0) {
            normalized = normalized.substring(0, tagIndex);
        }
        int lastDot = normalized.lastIndexOf('.');
        String name = lastDot >= 0 ? normalized.substring(lastDot + 1) : normalized;
        return stripBackticks(name);
    }

    public static String stripBackticks(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("`") && trimmed.endsWith("`") && trimmed.length() >= 2) {
            String body = trimmed.substring(1, trimmed.length() - 1);
            return body.replace("\\`", "`").replace("\\\\", "\\");
        }
        return trimmed;
    }

    public static List<String> splitPathSegments(String path) {
        List<String> parts = new ArrayList<>();
        if (path == null || path.isBlank()) {
            return parts;
        }
        String normalized = path;
        int tagIndex = normalized.indexOf('{');
        if (tagIndex >= 0) {
            normalized = normalized.substring(0, tagIndex);
        }
        StringBuilder current = new StringBuilder();
        boolean inBacktick = false;
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch == '`') {
                inBacktick = !inBacktick;
                current.append(ch);
                continue;
            }
            if (ch == '.' && !inBacktick) {
                parts.add(stripBackticks(current.toString()));
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        if (current.length() > 0) {
            parts.add(stripBackticks(current.toString()));
        }
        return parts;
    }

    public static boolean startsWithSegments(List<String> pathSegments, List<String> prefixSegments) {
        if (prefixSegments == null || prefixSegments.isEmpty()) {
            return true;
        }
        if (pathSegments == null || pathSegments.size() < prefixSegments.size()) {
            return false;
        }
        for (int i = 0; i < prefixSegments.size(); i++) {
            if (!pathSegments.get(i).equalsIgnoreCase(prefixSegments.get(i))) {
                return false;
            }
        }
        return true;
    }

    public static String normalizeOrderDirection(String direction) {
        if (direction == null) {
            return "ASC";
        }
        String normalized = direction.trim().toUpperCase(Locale.ROOT);
        return "DESC".equals(normalized) ? "DESC" : "ASC";
    }
}
