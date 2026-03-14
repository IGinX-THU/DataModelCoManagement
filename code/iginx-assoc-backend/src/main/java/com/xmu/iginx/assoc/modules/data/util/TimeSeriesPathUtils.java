package com.xmu.iginx.assoc.modules.data.util;

import com.xmu.iginx.assoc.common.exception.BizException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 时序路径处理工具。
 */
public final class TimeSeriesPathUtils {

    private static final String ROOT_PREFIX = "root.";

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
     * 判断是否包含 root 前缀。
     *
     * @param path 路径
     * @return 是否包含 root 前缀
     */
    public static boolean hasRootPrefix(String path) {
        return path != null && path.trim().startsWith(ROOT_PREFIX);
    }

    /**
     * 去除 root 前缀。
     *
     * @param path 路径
     * @return 去除后的路径
     */
    public static String stripRootPrefix(String path) {
        String normalized = normalizePath(path);
        if (normalized.startsWith(ROOT_PREFIX)) {
            return normalized.substring(ROOT_PREFIX.length());
        }
        return normalized;
    }

    /**
     * 判断路径是否位于前缀之下。
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

    /**
     * 将输入路径解析为挂载路径下的完整路径。
     *
     * @param input 输入路径
     * @param mountPath 挂载路径
     * @param allowSameAsMount 是否允许与挂载路径相同
     * @return 解析后的完整路径
     */
    public static String resolvePathUnderMount(String input, String mountPath, boolean allowSameAsMount) {
        String normalizedMount = normalizePath(mountPath);
        if (normalizedMount.isEmpty()) {
            throw BizException.badRequest("挂载路径不能为空");
        }
        String normalizedInput = normalizePath(input);
        if (normalizedInput.isEmpty()) {
            if (allowSameAsMount) {
                return normalizedMount;
            }
            throw BizException.badRequest("路径不能为空");
        }

        boolean mountHasRoot = hasRootPrefix(normalizedMount);
        boolean inputHasRoot = hasRootPrefix(normalizedInput);
        String mountWithoutRoot = stripRootPrefix(normalizedMount);

        if (mountHasRoot) {
            // 挂载路径包含 root 前缀
            if (inputHasRoot) {
                if (!startsWithPath(normalizedInput, normalizedMount)) {
                    throw BizException.badRequest("路径必须位于挂载路径下");
                }
                return ensureNotSame(normalizedInput, normalizedMount, allowSameAsMount);
            }
            if (startsWithPath(normalizedInput, mountWithoutRoot)) {
                String withRoot = ROOT_PREFIX + normalizedInput;
                return ensureNotSame(withRoot, normalizedMount, allowSameAsMount);
            }
            String combined = joinPath(normalizedMount, normalizedInput);
            return ensureNotSame(combined, normalizedMount, allowSameAsMount);
        }

        if (inputHasRoot) {
            // 输入包含 root 前缀时先剥离再校验
            String stripped = stripRootPrefix(normalizedInput);
            if (!startsWithPath(stripped, normalizedMount)) {
                throw BizException.badRequest("路径必须位于挂载路径下");
            }
            return ensureNotSame(stripped, normalizedMount, allowSameAsMount);
        }

        if (startsWithPath(normalizedInput, normalizedMount)) {
            return ensureNotSame(normalizedInput, normalizedMount, allowSameAsMount);
        }
        String combined = joinPath(normalizedMount, normalizedInput);
        return ensureNotSame(combined, normalizedMount, allowSameAsMount);
    }

    /**
     * IoTDB 存储引擎在查询时会自动补上 root 前缀，因此挂载路径需要去掉 root.
     *
     * @param mountPath 挂载路径
     * @return 规范化后的挂载路径
     */
    public static String normalizeIotdbMountPath(String mountPath) {
        String normalized = normalizePath(mountPath);
        if (normalized.isEmpty()) {
            return normalized;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith(ROOT_PREFIX)) {
            return normalized.substring(ROOT_PREFIX.length());
        }
        return normalized;
    }

    /**
     * 批量解析路径到挂载路径下。
     *
     * @param paths 输入路径列表
     * @param mountPath 挂载路径
     * @param allowSameAsMount 是否允许与挂载路径相同
     * @return 解析后的路径列表
     */
    public static List<String> resolvePathsUnderMount(List<String> paths, String mountPath, boolean allowSameAsMount) {
        if (paths == null || paths.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> resolved = new ArrayList<>();
        for (String path : paths) {
            resolved.add(resolvePathUnderMount(path, mountPath, allowSameAsMount));
        }
        return resolved;
    }

    /**
     * 校验路径是否允许与挂载路径相同。
     *
     * @param path 解析后的路径
     * @param mountPath 挂载路径
     * @param allowSameAsMount 是否允许相同
     * @return 合法路径
     */
    private static String ensureNotSame(String path, String mountPath, boolean allowSameAsMount) {
        if (!allowSameAsMount && path.equals(mountPath)) {
            throw BizException.badRequest("路径不能等于挂载路径");
        }
        return path;
    }
}
