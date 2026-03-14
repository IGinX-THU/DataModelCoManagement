package com.xmu.iginx.assoc.modules.data.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 结构化数据键生成器。
 */
public final class StructuredKeyGenerator {

    private StructuredKeyGenerator() {
    }

    /**
     * 从数据映射中提取内部键。
     *
     * @param map 数据映射
     * @return 内部键或 null
     */
    public static Long extractInternalKey(Map<String, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        Object raw = map.get(IginxStructuredUtils.INTERNAL_KEY);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 解析键值，支持内部键、哈希键或随机键。
     *
     * @param keyFields 键字段集合
     * @param allowRandom 是否允许随机键
     * @return 键值
     */
    public static long resolveKey(Map<String, ?> keyFields, boolean allowRandom) {
        Long internal = extractInternalKey(keyFields);
        if (internal != null) {
            return internal;
        }
        if (keyFields == null || keyFields.isEmpty()) {
            return allowRandom ? randomKey() : 0L;
        }
        return hashKey(keyFields);
    }

    /**
     * 生成随机键（避免保留键）。
     *
     * @return 随机键
     */
    public static long randomKey() {
        long candidate;
        do {
            candidate = ThreadLocalRandom.current().nextLong(0, Long.MAX_VALUE);
        } while (IginxStructuredUtils.isReservedKey(candidate));
        return candidate;
    }

    /**
     * 按业务字段生成哈希键。
     *
     * @param keyFields 键字段集合
     * @return 哈希键
     */
    public static long hashKey(Map<String, ?> keyFields) {
        TreeMap<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<String, ?> entry : keyFields.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            if (IginxStructuredUtils.isInternalKey(entry.getKey())) {
                continue;
            }
            sorted.put(entry.getKey(), entry.getValue());
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            // 使用不可见分隔符避免键值冲突
            builder.append(entry.getKey()).append('=').append(stringify(entry.getValue())).append('\u001F');
        }
        byte[] digest = sha256(builder.toString());
        long value = ByteBuffer.wrap(digest).getLong();
        value = value & Long.MAX_VALUE;
        while (IginxStructuredUtils.isReservedKey(value)) {
            value = value == 0 ? 1 : value - 1;
        }
        return value;
    }

    /**
     * 计算字符串的 SHA-256 摘要。
     *
     * @param value 字符串
     * @return 摘要字节
     */
    private static byte[] sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * 将值转换为可稳定哈希的字符串。
     *
     * @param value 原始值
     * @return 字符串
     */
    private static String stringify(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        return String.valueOf(value);
    }
}
