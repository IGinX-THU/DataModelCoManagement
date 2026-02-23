package com.xmu.iginx.assoc.modules.data.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public final class TimeParser {

    private static final List<DateTimeFormatter> FALLBACK_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ISO_LOCAL_DATE
    );

    private TimeParser() {
    }

    public static long parseToMillis(String value, String format) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("时间字段不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.chars().allMatch(Character::isDigit)) {
            long numeric = Long.parseLong(trimmed);
            if (trimmed.length() <= 10) {
                return numeric * 1000;
            }
            if (trimmed.length() <= 13) {
                return numeric;
            }
            return numeric / 1_000_000;
        }
        if (format != null && !format.isBlank()) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format.trim());
            return parseWithFormatter(trimmed, formatter);
        }
        for (DateTimeFormatter formatter : FALLBACK_FORMATTERS) {
            try {
                return parseWithFormatter(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new IllegalArgumentException("无法解析时间格式: " + value);
    }

    private static long parseWithFormatter(String value, DateTimeFormatter formatter) {
        if (formatter == DateTimeFormatter.ISO_LOCAL_DATE) {
            LocalDate date = LocalDate.parse(value, formatter);
            return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        LocalDateTime dateTime = LocalDateTime.parse(value, formatter);
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static long toNano(long millis) {
        return millis * 1_000_000;
    }

    public static long toMillis(long nanos) {
        return nanos / 1_000_000;
    }

    public static String formatMillis(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
