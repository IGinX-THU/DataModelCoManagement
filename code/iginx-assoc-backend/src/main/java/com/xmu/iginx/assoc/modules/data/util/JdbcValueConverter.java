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

    private static Object convertFromLocalDate(LocalDate date, int sqlType) {
        return switch (sqlType) {
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> Timestamp.valueOf(date.atStartOfDay());
            case Types.DATE -> Date.valueOf(date);
            default -> date;
        };
    }

    private static Object convertFromLocalDateTime(LocalDateTime dateTime, int sqlType) {
        return switch (sqlType) {
            case Types.DATE -> Date.valueOf(dateTime.toLocalDate());
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> Timestamp.valueOf(dateTime);
            default -> dateTime;
        };
    }

    private static Object convertFromLocalTime(LocalTime time, int sqlType) {
        return switch (sqlType) {
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> Time.valueOf(time);
            default -> time;
        };
    }

    private static Object convertFromDate(java.util.Date date, int sqlType) {
        return switch (sqlType) {
            case Types.DATE -> new Date(date.getTime());
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> new Time(date.getTime());
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> new Timestamp(date.getTime());
            default -> date;
        };
    }

    private static long parseToMillis(String value) {
        return TimeParser.parseToMillis(value, null);
    }

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
        throw new IllegalArgumentException("鏃堕棿瀛楁鏍煎紡涓嶆纭?: " + value);
    }

    private static boolean parseBoolean(String value) {
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "true", "1", "yes", "y" -> true;
            case "false", "0", "no", "n" -> false;
            default -> throw new IllegalArgumentException("甯冨皵瀛楁鏍煎紡涓嶆纭?: " + value);
        };
    }
}
