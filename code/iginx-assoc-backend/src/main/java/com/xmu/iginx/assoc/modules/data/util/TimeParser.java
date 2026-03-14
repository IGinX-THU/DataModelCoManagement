package com.xmu.iginx.assoc.modules.data.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 时间解析工具，支持多种格式与时间精度转换。
 */
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

    /**
     * 将时间字符串解析为毫秒时间戳。
     *
     * @param value 时间字符串
     * @param format 指定格式，可为空
     * @return 毫秒时间戳
     */
    public static long parseToMillis(String value, String format) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("时间字段不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.chars().allMatch(Character::isDigit)) {
            long numeric = Long.parseLong(trimmed);
            if (trimmed.length() <= 10) {
                // 秒级时间戳
                return numeric * 1000;
            }
            if (trimmed.length() <= 13) {
                // 毫秒级时间戳
                return numeric;
            }
            // 纳秒级或更高精度，回退为毫秒
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

    /**
     * 使用指定格式解析时间字符串。
     *
     * @param value 时间字符串
     * @param formatter 格式化器
     * @return 毫秒时间戳
     */
    private static long parseWithFormatter(String value, DateTimeFormatter formatter) {
        if (formatter == DateTimeFormatter.ISO_LOCAL_DATE) {
            LocalDate date = LocalDate.parse(value, formatter);
            return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        LocalDateTime dateTime = LocalDateTime.parse(value, formatter);
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 毫秒转纳秒。
     *
     * @param millis 毫秒
     * @return 纳秒
     */
    public static long toNano(long millis) {
        return millis * 1_000_000;
    }

    /**
     * 纳秒转毫秒。
     *
     * @param nanos 纳秒
     * @return 毫秒
     */
    public static long toMillis(long nanos) {
        return nanos / 1_000_000;
    }

    /**
     * 将毫秒时间戳格式化为标准字符串。
     *
     * @param millis 毫秒时间戳
     * @return 格式化字符串
     */
    public static String formatMillis(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
