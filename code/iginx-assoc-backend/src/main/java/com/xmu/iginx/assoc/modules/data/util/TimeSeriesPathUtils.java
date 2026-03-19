package com.xmu.iginx.assoc.modules.data.util;

/**
 * 时序路径处理工具。
 */
public final class TimeSeriesPathUtils {

    private TimeSeriesPathUtils() {
    }

    /**
     * 规范化路径（去空格与尾部点）。
     *
     * @param path 原始路径
     * @return 规范化路径
     */
    public static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String trimmed = path.trim();
        while (trimmed.endsWith(".")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * 判断路径是否位于指定前缀之下。
     *
     * @param path 路径
     * @param prefix 前缀
     * @return 是否匹配
     */
    public static boolean startsWithPath(String path, String prefix) {
        if (path == null || prefix == null) {
            return false;
        }
        if (path.equals(prefix)) {
            return true;
        }
        return path.startsWith(prefix + ".");
    }

    /**
     * 拼接路径。
     *
     * @param prefix 前缀
     * @param suffix 后缀
     * @return 拼接后的路径
     */
    public static String joinPath(String prefix, String suffix) {
        String left = normalizePath(prefix);
        String right = normalizePath(suffix);
        if (left.isEmpty()) {
            return right;
        }
        if (right.isEmpty()) {
            return left;
        }
        if (left.endsWith(".")) {
            left = left.substring(0, left.length() - 1);
        }
        if (right.startsWith(".")) {
            right = right.substring(1);
        }
        return left + "." + right;
    }
}
