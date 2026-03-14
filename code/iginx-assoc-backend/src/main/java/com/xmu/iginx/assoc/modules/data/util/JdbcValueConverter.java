package com.xmu.iginx.assoc.modules.data.util;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * JDBC 参数值转换工具，统一处理数字、时间等类型。
 */
public final class JdbcValueConverter {

    private static final List<DateTimeFormatter> TIME_FORMATTERS = List.of(
        DateTimeFormatter.ISO_LOCAL_TIME,
        DateTimeFormatter.ofPattern("HH:mm:ss"),
        DateTimeFormatter.ofPattern("HH:mm")
    );

    private JdbcValueConverter() {
    }

    /**
     * 将输入值按 JDBC 类型进行转换。
     *
     * @param value 原始值
     * @param sqlType JDBC 类型
     * @return 转换后的值
     */
    public static Object convert(Object value, Integer sqlType) {
        if (value == null || sqlType == null) {
            return value;
        }
        if (value instanceof String str) {
            return convertFromString(str, sqlType);
        }
        if (value instanceof Number number) {
            return convertFromNumber(number, sqlType);
        }
        if (value instanceof LocalDate date) {
            return convertFromLocalDate(date, sqlType);
        }
        if (value instanceof LocalDateTime dateTime) {
            return convertFromLocalDateTime(dateTime, sqlType);
        }
        if (value instanceof LocalTime time) {
            return convertFromLocalTime(time, sqlType);
        }
        if (value instanceof java.util.Date date) {
            return convertFromDate(date, sqlType);
        }
        return value;
    }

    /**
     * 从字符串转换为指定 JDBC 类型。
     *
     * @param value 字符串值
     * @param sqlType JDBC 类型
     * @return 转换后的值
     */
    private static Object convertFromString(String value, int sqlType) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return switch (sqlType) {
            case Types.BIGINT -> Long.parseLong(trimmed);
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> Integer.parseInt(trimmed);
            case Types.FLOAT, Types.REAL, Types.DOUBLE -> Double.parseDouble(trimmed);
            case Types.NUMERIC, Types.DECIMAL -> new BigDecimal(trimmed);
            case Types.BOOLEAN, Types.BIT -> parseBoolean(trimmed);
            case Types.DATE -> new Date(parseToMillis(trimmed));
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> new Time(parseToMillisForTime(trimmed));
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> new Timestamp(parseToMillis(trimmed));
            default -> trimmed;
        };
    }

    /**
     * 从数值转换为指定 JDBC 类型。
     *
     * @param number 数值
     * @param sqlType JDBC 类型
     * @return 转换后的值
     */
    private static Object convertFromNumber(Number number, int sqlType) {
        return switch (sqlType) {
            case Types.BIGINT -> number.longValue();
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> number.intValue();
            case Types.FLOAT, Types.REAL, Types.DOUBLE -> number.doubleValue();
            case Types.NUMERIC, Types.DECIMAL -> new BigDecimal(number.toString());
            case Types.BOOLEAN, Types.BIT -> number.intValue() != 0;
            case Types.DATE -> new Date(parseToMillis(number.toString()));
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> new Time(parseToMillisForTime(number.toString()));
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> new Timestamp(parseToMillis(number.toString()));
            default -> number;
        };
    }

    /**
     * 从 LocalDate 转换为指定 JDBC 类型。
     *
     * @param date 日期
     * @param sqlType JDBC 类型
     * @return 转换后的值
     */
    private static Object convertFromLocalDate(LocalDate date, int sqlType) {
        return switch (sqlType) {
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> Timestamp.valueOf(date.atStartOfDay());
            case Types.DATE -> Date.valueOf(date);
            default -> date;
        };
    }

    /**
     * 从 LocalDateTime 转换为指定 JDBC 类型。
     *
     * @param dateTime 日期时间
     * @param sqlType JDBC 类型
     * @return 转换后的值
     */
    private static Object convertFromLocalDateTime(LocalDateTime dateTime, int sqlType) {
        return switch (sqlType) {
            case Types.DATE -> Date.valueOf(dateTime.toLocalDate());
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> Timestamp.valueOf(dateTime);
            default -> dateTime;
        };
    }

    /**
     * 从 LocalTime 转换为指定 JDBC 类型。
     *
     * @param time 时间
     * @param sqlType JDBC 类型
     * @return 转换后的值
     */
    private static Object convertFromLocalTime(LocalTime time, int sqlType) {
        return switch (sqlType) {
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> Time.valueOf(time);
            default -> time;
        };
    }

    /**
     * 从 Date 转换为指定 JDBC 类型。
     *
     * @param date 日期
     * @param sqlType JDBC 类型
     * @return 转换后的值
     */
    private static Object convertFromDate(java.util.Date date, int sqlType) {
        return switch (sqlType) {
            case Types.DATE -> new Date(date.getTime());
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> new Time(date.getTime());
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> new Timestamp(date.getTime());
            default -> date;
        };
    }

    /**
     * 将时间字符串解析为毫秒时间戳。
     *
     * @param value 时间字符串
     * @return 毫秒时间戳
     */
    private static long parseToMillis(String value) {
        return TimeParser.parseToMillis(value, null);
    }

    /**
     * 将时间字符串解析为当天的毫秒时间戳（仅保留时间部分）。
     *
     * @param value 时间字符串
     * @return 毫秒时间戳
     */
    private static long parseToMillisForTime(String value) {
        String trimmed = value.trim();
        for (DateTimeFormatter formatter : TIME_FORMATTERS) {
            try {
                LocalTime time = LocalTime.parse(trimmed, formatter);
                return time.atDate(LocalDate.ofEpochDay(0))
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
            } catch (DateTimeParseException ignored) {
            }
        }
        if (trimmed.chars().allMatch(Character::isDigit)) {
            long millis = TimeParser.parseToMillis(trimmed, null);
            return Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .atDate(LocalDate.ofEpochDay(0))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        }
        throw new IllegalArgumentException("时间字段格式不正确: " + value);
    }

    /**
     * 解析布尔值字符串。
     *
     * @param value 原始值
     * @return 布尔值
     */
    private static boolean parseBoolean(String value) {
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "true", "1", "yes", "y" -> true;
            case "false", "0", "no", "n" -> false;
            default -> throw new IllegalArgumentException("布尔字段格式不正确: " + value);
        };
    }
}
