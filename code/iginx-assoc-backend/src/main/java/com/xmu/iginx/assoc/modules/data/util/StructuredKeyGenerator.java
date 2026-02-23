package com.xmu.iginx.assoc.modules.data.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

public final class StructuredKeyGenerator {

    private StructuredKeyGenerator() {
    }

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

    public static long randomKey() {
        long candidate;
        do {
            candidate = ThreadLocalRandom.current().nextLong(0, Long.MAX_VALUE);
        } while (IginxStructuredUtils.isReservedKey(candidate));
        return candidate;
    }

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

    private static byte[] sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
    }

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
