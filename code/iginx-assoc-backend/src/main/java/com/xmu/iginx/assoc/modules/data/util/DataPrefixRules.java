package com.xmu.iginx.assoc.modules.data.util;

import com.xmu.iginx.assoc.common.exception.BizException;

/**
 * 数据前缀规则工具类。
 */
public final class DataPrefixRules {

    public static final String TS_PREFIX = "ts";
    public static final String RT_PREFIX = "rt";
    public static final String MODEL_PREFIX = "models";

    private DataPrefixRules() {
    }

    /**
     * 校验时序路径前缀是否以 ts 开头。
     *
     * @param path 规范化后的路径
     */
    public static void validateTimeSeriesPrefix(String path) {
        if (!startsWithPrefix(path, TS_PREFIX)) {
            throw BizException.badRequest("时序数据路径前缀必须以 ts 开头，例如：ts.*");
        }
    }

    /**
     * 校验结构化路径前缀是否以 rt 开头。
     *
     * @param path 规范化后的路径
     */
    public static void validateStructuredPrefix(String path) {
        if (!startsWithPrefix(path, RT_PREFIX)) {
            throw BizException.badRequest("结构化数据路径前缀必须以 rt 开头，例如：rt.*");
        }
    }

    /**
     * 规范化结构化 schema 路径，确保带有 rt 前缀。
     *
     * @param schema 原始 schema
     * @return 规范化后的 schema
     */
    public static String normalizeStructuredSchema(String schema) {
        String normalized = TimeSeriesPathUtils.stripRootPrefix(schema);
        if (normalized == null || normalized.isBlank()) {
            return "";
        }
        if (startsWithPrefix(normalized, RT_PREFIX)) {
            return normalized;
        }
        return RT_PREFIX + "." + normalized;
    }

    /**
     * 判断前缀是否为 models。
     *
     * @param prefix 前缀
     * @return 是否为 models
     */
    public static boolean isModelPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return false;
        }
        return MODEL_PREFIX.equalsIgnoreCase(prefix.trim());
    }

    /**
     * 规范化模型前缀，若不合法则返回 models。
     *
     * @param prefix 前缀
     * @return 规范化后的前缀
     */
    public static String normalizeModelPrefix(String prefix) {
        return isModelPrefix(prefix) ? prefix.trim() : MODEL_PREFIX;
    }

    /**
     * 判断路径是否以指定前缀段开头。
     *
     * @param path 路径
     * @param prefix 期望前缀
     * @return 是否匹配
     */
    public static boolean startsWithPrefix(String path, String prefix) {
        if (path == null || path.isBlank() || prefix == null || prefix.isBlank()) {
            return false;
        }
        String normalized = TimeSeriesPathUtils.normalizePath(path);
        if (normalized.isBlank()) {
            return false;
        }
        String[] segments = normalized.split("\\.");
        if (segments.length == 0) {
            return false;
        }
        return prefix.equalsIgnoreCase(segments[0]);
    }
}
