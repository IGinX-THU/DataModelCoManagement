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

/**
 * IGinX 结构化数据工具类，提供路径与类型相关工具方法。
 */
public final class IginxStructuredUtils {

    public static final String INTERNAL_KEY = "_iginx_key";
    // 使用最大值会触发 IGinX 路由异常，保留一个安全值作为占位 Key
    public static final long DUMMY_KEY = Long.MAX_VALUE - 1;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private IginxStructuredUtils() {
    }

    /**
     * 为 SQL 标识符添加必要的反引号。
     *
     * @param identifier 标识符
     * @return 处理后的标识符
     */
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

    /**
     * 构建表路径。
     *
     * @param schema schema 路径
     * @param table 表名
     * @return 表路径
     */
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
        return segments.stream().map(IginxStructuredUtils::quoteIdentifier)
            .collect(java.util.stream.Collectors.joining("."));
    }

    /**
     * 将参数替换为 SQL 字面量。
     *
     * @param sql SQL 模板
     * @param params 参数列表
     * @return 替换后的 SQL
     */
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

    /**
     * 将值转换为 SQL 字面量。
     *
     * @param value 值
     * @return SQL 字面量
     */
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

    /**
     * 生成带引号的字符串字面量。
     *
     * @param value 字符串
     * @return SQL 字符串字面量
     */
    private static String quoteString(String value) {
        String escaped = value == null ? "" : value.replace("\\", "\\\\").replace("'", "\\'");
        return "'" + escaped + "'";
    }

    /**
     * 将 IGinX 类型映射为 JDBC 类型。
     *
     * @param columnTypes 列类型映射
     * @return JDBC 类型映射
     */
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

    /**
     * 将 IGinX 类型转换为 JDBC 类型。
     *
     * @param type IGinX 类型
     * @return JDBC 类型
     */
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

    /**
     * 规范化结构化查询的表头。
     *
     * @param headers 原始表头
     * @return 规范化后的表头
     */
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
            result.add(extractColumnName(trimmed));
        }
        return result;
    }

    /**
     * 从路径中提取列名。
     *
     * @param path 路径
     * @return 列名
     */
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
        String columnName = stripBackticks(name);
        if (columnName == null) {
            return null;
        }
        if ("KEY".equalsIgnoreCase(columnName) || INTERNAL_KEY.equalsIgnoreCase(columnName)) {
            return "KEY";
        }
        return columnName;
    }

    /**
     * 去除反引号并还原转义。
     *
     * @param value 字符串
     * @return 处理后的字符串
     */
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

    /**
     * 按路径段拆分，支持反引号包裹的段。
     *
     * @param path 路径
     * @return 段列表
     */
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

    /**
     * 判断路径段是否以前缀段开始。
     *
     * @param pathSegments 路径段
     * @param prefixSegments 前缀段
     * @return 是否匹配
     */
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

    /**
     * 规范化排序方向。
     *
     * @param direction 排序方向
     * @return 规范化后的排序方向
     */
    public static String normalizeOrderDirection(String direction) {
        if (direction == null) {
            return "ASC";
        }
        String normalized = direction.trim().toUpperCase(Locale.ROOT);
        return "DESC".equals(normalized) ? "DESC" : "ASC";
    }
}
