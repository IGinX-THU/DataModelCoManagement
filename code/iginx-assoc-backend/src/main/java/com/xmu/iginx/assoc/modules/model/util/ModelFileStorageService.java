package com.xmu.iginx.assoc.modules.model.util;

import cn.edu.tsinghua.iginx.session.SessionQueryDataSet;
import cn.edu.tsinghua.iginx.thrift.DataType;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.common.exception.ExceptionMessageUtils;
import com.xmu.iginx.assoc.framework.iginx.IginxFileSystemRegistrar;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 模型文件存储服务，基于 Iginx 存储文件分块。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelFileStorageService {

    private static final String URI_PREFIX = "iginx://";
    private static final int CHUNK_SIZE = 1024 * 10;
    private static final int INSERT_BATCH_SIZE = 512;
    private static final int QUERY_BATCH_SIZE = 1024;

    private final IginxStorageWrapper iginxStorageWrapper;
    private final IginxFileSystemRegistrar fileSystemRegistrar;

    /**
     * 存储模型文件并生成存储元信息。
     *
     * @param file 模型文件
     * @param framework 文件类型或框架
     * @param profileId 档案 ID
     * @param version 版本号
     * @return 存储信息
     * @throws IOException 读取文件失败
     */
    public StoredFile store(MultipartFile file, String framework, Long profileId, String version) throws IOException {
        ensureFileSystemReady();
        String fileName = resolveFileName(file);
        String storageUri = buildStorageUri(framework, profileId, version, fileName);
        String iginxPath = toIginxPath(storageUri);
        MessageDigest digest = createMd5Digest();

        List<Long> keys = new ArrayList<>();
        List<Object[]> rows = new ArrayList<>();
        long index = 0;

        try (InputStream inputStream = file.getInputStream()) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                byte[] chunk = Arrays.copyOf(buffer, read);
                digest.update(chunk);
                keys.add(index++);
                rows.add(new Object[]{chunk});
                if (keys.size() >= INSERT_BATCH_SIZE) {
                    // 分批写入，避免单次请求过大
                    flushInsert(iginxPath, keys, rows);
                }
            }
            flushInsert(iginxPath, keys, rows);
        }

        if (index == 0) {
            throw BizException.badRequest("模型文件不能为空");
        }
        String md5 = toHex(digest.digest());
        return new StoredFile(storageUri, iginxPath, fileName, md5);
    }

    /**
     * 校验文件是否存在（通过查询首块数据判断）。
     *
     * @param storageUri 存储地址
     */
    public void ensureExists(String storageUri) {
        ensureFileSystemReady();
        String iginxPath = toIginxPath(storageUri);
        List<String> paths = new ArrayList<>();
        paths.add(iginxPath);
        SessionQueryDataSet dataSet = iginxStorageWrapper.executeWithSession(
            session -> session.queryData(paths, 0L, 1L)
        );
        if (dataSet.getKeys() == null || dataSet.getKeys().length == 0) {
            throw BizException.badRequest("模型文件不存在");
        }
    }

    /**
     * 将存储文件按块输出到指定流。
     *
     * @param storageUri 存储地址
     * @param fileSize 文件大小
     * @param outputStream 输出流
     * @throws IOException 写入失败
     */
    public void writeTo(String storageUri, Long fileSize, OutputStream outputStream) throws IOException {
        ensureFileSystemReady();
        String iginxPath = toIginxPath(storageUri);
        long totalChunks = resolveTotalChunks(fileSize);
        long start = 0;
        boolean hasAny = false;

        while (totalChunks < 0 || start < totalChunks) {
            long queryStart = start;
            long queryEndExclusive = totalChunks < 0
                ? start + QUERY_BATCH_SIZE
                : Math.min(totalChunks, start + QUERY_BATCH_SIZE);
            List<String> paths = new ArrayList<>();
            paths.add(iginxPath);
            SessionQueryDataSet dataSet = iginxStorageWrapper.executeWithSession(
                session -> session.queryData(paths, queryStart, queryEndExclusive)
            );
            long[] keys = dataSet.getKeys();
            if (keys == null || keys.length == 0) {
                if (!hasAny) {
                    throw BizException.badRequest("模型文件不存在");
                }
                break;
            }
            hasAny = true;
            // 将查询到的分块按顺序写入输出流
            writeChunks(keys, dataSet.getValues(), queryStart, queryEndExclusive, totalChunks, outputStream);
            start = queryEndExclusive;
        }
    }

    /**
     * 将存储文件读取为字节数组。
     *
     * @param storageUri 存储地址
     * @param fileSize 文件大小
     * @return 文件字节
     */
    public byte[] readAsBytes(String storageUri, Long fileSize) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            writeTo(storageUri, fileSize, outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw BizException.internal(ExceptionMessageUtils.buildDetailedMessage("读取模型文件失败", ex), ex);
        }
    }

    /**
     * 删除存储文件。
     *
     * @param storageUri 存储地址
     */
    public void delete(String storageUri) {
        ensureFileSystemReady();
        if (!StringUtils.hasText(storageUri)) {
            return;
        }
        String iginxPath;
        try {
            iginxPath = toIginxPath(storageUri);
        } catch (BizException ex) {
            log.warn("删除模型文件失败，路径无效: {}", storageUri);
            return;
        }
        try {
            iginxStorageWrapper.executeWithSession(session -> {
                List<String> paths = new ArrayList<>();
                paths.add(iginxPath);
                session.deleteColumns(paths);
                return null;
            });
        } catch (BizException ex) {
            log.warn("删除模型文件失败，忽略异常: {}", storageUri);
        }
    }

    /**
     * 将查询结果按顺序写入输出流。
     */
    private void writeChunks(long[] keys,
                             List<List<Object>> values,
                             long startInclusive,
                             long endExclusive,
                             long totalChunks,
                             OutputStream outputStream) throws IOException {
        Map<Long, byte[]> chunkMap = new HashMap<>();
        for (int i = 0; i < keys.length; i++) {
            if (values == null || i >= values.size()) {
                continue;
            }
            List<Object> row = values.get(i);
            if (row == null || row.isEmpty()) {
                continue;
            }
            Object value = row.get(0);
            if (value instanceof byte[] bytes) {
                chunkMap.put(keys[i], bytes);
            }
        }

        if (totalChunks < 0) {
            if (chunkMap.size() < keys.length) {
                throw BizException.internal("模型文件内容缺失");
            }
            long[] sortedKeys = Arrays.copyOf(keys, keys.length);
            Arrays.sort(sortedKeys);
            for (long key : sortedKeys) {
                byte[] chunk = chunkMap.get(key);
                if (chunk == null) {
                    throw BizException.internal("模型文件内容缺失");
                }
                outputStream.write(chunk);
            }
            outputStream.flush();
            return;
        }

        long limitExclusive = Math.min(endExclusive, totalChunks);
        for (long expected = startInclusive; expected < limitExclusive; expected++) {
            byte[] chunk = chunkMap.get(expected);
            if (chunk == null) {
                throw BizException.internal("模型文件内容缺失");
            }
            outputStream.write(chunk);
        }
        outputStream.flush();
    }

    /**
     * 将分块数据批量写入 Iginx。
     */
    private void flushInsert(String iginxPath, List<Long> keys, List<Object[]> rows) {
        if (keys.isEmpty()) {
            return;
        }
        long[] keyArray = keys.stream().mapToLong(Long::longValue).toArray();
        Object[] valuesList = rows.toArray(Object[]::new);
        iginxStorageWrapper.executeWithSession(session -> {
            List<String> paths = new ArrayList<>();
            paths.add(iginxPath);
            List<DataType> types = new ArrayList<>();
            types.add(DataType.BINARY);
            session.insertRowRecords(paths, keyArray, valuesList, types, null);
            return null;
        });
        keys.clear();
        rows.clear();
    }

    /**
     * 解析文件名并清理路径信息。
     */
    private String resolveFileName(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (!StringUtils.hasText(original)) {
            throw BizException.badRequest("模型文件名不能为空");
        }
        String cleaned = StringUtils.cleanPath(original);
        int slash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        String fileName = slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
        if (!StringUtils.hasText(fileName)) {
            throw BizException.badRequest("模型文件名不能为空");
        }
        return fileName;
    }

    /**
     * 构建存储地址。
     */
    private String buildStorageUri(String framework, Long profileId, String version, String fileName) {
        String modelRoot = resolveModelRoot();
        String safeFramework = sanitizeUriSegment(framework).toLowerCase(Locale.ROOT);
        String safeVersion = sanitizeUriSegment(version);
        String safeFileName = sanitizeUriSegment(fileName);
        return URI_PREFIX + modelRoot + "/" + safeFramework + "/" + profileId + "/" + safeVersion + "/" + safeFileName;
    }

    /**
     * 清理 URI 段，避免非法字符。
     */
    private String sanitizeUriSegment(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        String trimmed = value.trim();
        String replaced = trimmed.replace("\\", "_").replace("/", "_");
        if (!StringUtils.hasText(replaced)) {
            return "unknown";
        }
        return replaced;
    }

    /**
     * 获取模型文件存储根路径配置。
     */
    private String resolveModelRoot() {
        String root = fileSystemRegistrar.getDataPrefix();
        if (!StringUtils.hasText(root)) {
            throw BizException.internal("模型文件存储前缀未配置");
        }
        return sanitizeUriSegment(root);
    }

    /**
     * 将存储地址转换为 Iginx 路径。
     */
    private String toIginxPath(String storageUri) {
        String raw = storageUri == null ? "" : storageUri.trim();
        if (!raw.startsWith(URI_PREFIX)) {
            throw BizException.badRequest("模型文件路径不合法");
        }
        String path = raw.substring(URI_PREFIX.length()).replace("\\", "/");
        String[] segments = path.split("/");
        List<String> sanitized = new ArrayList<>();
        for (String segment : segments) {
            if (!StringUtils.hasText(segment)) {
                continue;
            }
            sanitized.add(sanitizeSegment(segment));
        }
        if (sanitized.isEmpty()) {
            throw BizException.badRequest("模型文件路径不合法");
        }
        return String.join(".", sanitized);
    }

    /**
     * 确保 Iginx 文件系统已注册。
     */
    private void ensureFileSystemReady() {
        fileSystemRegistrar.ensureRegistered();
    }

    /**
     * 清理路径片段，仅保留字母数字与下划线。
     */
    private String sanitizeSegment(String segment) {
        String trimmed = segment.trim();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        String result = builder.toString();
        return result.isBlank() ? "unknown" : result;
    }

    /**
     * 计算总分块数，未知时返回 -1。
     */
    private long resolveTotalChunks(Long fileSize) {
        if (fileSize == null || fileSize <= 0) {
            return -1;
        }
        return (fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE;
    }

    /**
     * 创建 MD5 摘要器。
     */
    private MessageDigest createMd5Digest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (Exception ex) {
            throw BizException.internal(ExceptionMessageUtils.buildDetailedMessage("模型文件校验失败", ex), ex);
        }
    }

    /**
     * 转换为十六进制字符串。
     */
    private String toHex(byte[] hash) {
        StringBuilder builder = new StringBuilder();
        for (byte b : hash) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    /**
     * 存储结果信息。
     *
     * @param storageUri 存储地址
     * @param iginxPath Iginx 路径
     * @param fileName 文件名
     * @param md5 文件摘要
     */
    public record StoredFile(String storageUri, String iginxPath, String fileName, String md5) {
    }
}
