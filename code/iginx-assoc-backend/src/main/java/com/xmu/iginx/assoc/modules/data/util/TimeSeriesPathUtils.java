package com.xmu.iginx.assoc.modules.data.util;

import com.xmu.iginx.assoc.common.exception.BizException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TimeSeriesPathUtils {

    private static final String ROOT_PREFIX = "root.";

    private TimeSeriesPathUtils() {
    }

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

    public static boolean hasRootPrefix(String path) {
        return path != null && path.trim().startsWith(ROOT_PREFIX);
    }

    public static String stripRootPrefix(String path) {
        String normalized = normalizePath(path);
        if (normalized.startsWith(ROOT_PREFIX)) {
            return normalized.substring(ROOT_PREFIX.length());
        }
        return normalized;
    }

    public static boolean startsWithPath(String path, String prefix) {
        if (path == null || prefix == null) {
            return false;
        }
        if (path.equals(prefix)) {
            return true;
        }
        return path.startsWith(prefix + ".");
    }

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

    private static String ensureNotSame(String path, String mountPath, boolean allowSameAsMount) {
        if (!allowSameAsMount && path.equals(mountPath)) {
            throw BizException.badRequest("路径不能等于挂载路径");
        }
        return path;
    }
}
