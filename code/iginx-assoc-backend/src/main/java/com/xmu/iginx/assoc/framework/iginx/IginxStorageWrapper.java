package com.xmu.iginx.assoc.framework.iginx;

import cn.edu.tsinghua.iginx.session.Session;
import cn.edu.tsinghua.iginx.session.SessionExecuteSqlResult;
import cn.edu.tsinghua.iginx.thrift.FileChunk;
import cn.edu.tsinghua.iginx.utils.Pair;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.common.exception.ExceptionMessageUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * IGinX 会话包装器，提供 SQL 执行与重试能力。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IginxStorageWrapper {

    private static final String IGINX_UNAVAILABLE_MESSAGE = "IGinX 服务当前不可用，请检查服务状态后重试";
    private static final int LOAD_CSV_CHUNK_SIZE = 1024 * 1024;

    private final IginxConfig config;
    private volatile Session session;

    /**
     * 容器启动后初始化连接。
     */
    @PostConstruct
    public void init() {
        connect(false);
    }

    /**
     * 执行 SQL 语句，必要时进行一次重连重试。
     *
     * @param sql 待执行 SQL
     * @return 执行结果
     */
    public SessionExecuteSqlResult executeSql(String sql) {
        try {
            log.debug("Executing SQL: {}", sql);
            Session current = requireSession();
            return current.executeSql(sql);
        } catch (BizException e) {
            throw e;
        } catch (Exception firstEx) {
            // 已注册的存储引擎重复添加时直接忽略
            if (isDuplicateStorageEngineError(sql, firstEx)) {
                log.warn("IGinX duplicate storage engine detected, skip registration. sql={}", sql);
                return new SessionExecuteSqlResult();
            }
            if (!shouldRetry(firstEx)) {
                log.warn("IGinX execution rejected without reconnect. sql={}, message={}", sql, safeMessage(firstEx));
                throw toBizException(firstEx);
            }
            // 发生连接问题时尝试重连一次
            log.warn("IGinX execution failed, try reconnect once. sql={}", sql, firstEx);
            session = null;
            Session retry = requireSession();
            try {
                return retry.executeSql(sql);
            } catch (Exception secondEx) {
                // 重试阶段仍检测到重复注册
                if (isDuplicateStorageEngineError(sql, secondEx)) {
                    log.warn("IGinX duplicate storage engine detected, skip registration. sql={}", sql);
                    return new SessionExecuteSqlResult();
                }
                if (!shouldRetry(secondEx)) {
                    log.warn("IGinX execution rejected after reconnect. sql={}, message={}", sql, safeMessage(secondEx));
                    throw toBizException(secondEx);
                }
                log.error("IGinX execution failed after reconnect. sql={}", sql, secondEx);
                session = null;
                throw toBizException(secondEx);
            }
        }
    }

    /**
     * 在受控 Session 中执行回调，必要时重连一次。
     *
     * @param executor 会话执行器
     * @param <T> 返回类型
     * @return 执行结果
     */
    public <T> T executeWithSession(SessionExecutor<T> executor) {
        try {
            Session current = requireSession();
            return executor.apply(current);
        } catch (BizException e) {
            throw e;
        } catch (Exception firstEx) {
            if (!shouldRetry(firstEx)) {
                log.warn("IGinX session execution rejected without reconnect. message={}", safeMessage(firstEx));
                throw toBizException(firstEx);
            }
            // 发生连接问题时尝试重连一次
            log.warn("IGinX session execution failed, try reconnect once.", firstEx);
            session = null;
            Session retry = requireSession();
            try {
                return executor.apply(retry);
            } catch (Exception secondEx) {
                if (!shouldRetry(secondEx)) {
                    log.warn("IGinX session execution rejected after reconnect. message={}", safeMessage(secondEx));
                    throw toBizException(secondEx);
                }
                log.error("IGinX session execution failed after reconnect.", secondEx);
                session = null;
                throw toBizException(secondEx);
            }
        }
    }

    /**
     * 组装并执行 IGinX CSV 导入 SQL。
     * <p>
     * 语法参考《用户手册-v0.8.0》3.2.1.3：LOAD DATA FROM INFILE ... AS CSV INTO ... [SET KEY "colName"]。
     * </p>
     *
     * @param csvFilePath CSV 文件绝对路径
     * @param targetPath 导入目标路径前缀
     * @param keyColumn KEY 列名；为空时走自动生成 KEY
     * @return SQL 执行结果
     */
    public SessionExecuteSqlResult executeLoadDataFromCsv(String csvFilePath, String targetPath, String keyColumn) {
        Path csvPath = normalizeCsvFilePath(csvFilePath);
        String sql = buildLoadDataFromCsvSql(csvPath.toString(), targetPath, keyColumn);
        try {
            return executeWithSession(current -> {
                log.debug("Executing SQL: {}", sql);
                SessionExecuteSqlResult result = current.executeSql(sql);
                if (result != null && StringUtils.hasText(result.getParseErrorMsg())) {
                    throw BizException.badRequest(result.getParseErrorMsg().trim());
                }

                String loadCsvPath = result == null ? null : result.getLoadCsvPath();
                if (!StringUtils.hasText(loadCsvPath)) {
                    return result;
                }

                String uploadFileName = current.getSessionId() + "-" + System.currentTimeMillis() + ".csv";
                uploadCsvFileChunks(current, csvPath, uploadFileName);
                Pair<List<String>, Long> loadResult = current.executeLoadCSV(sql, uploadFileName);
                Long importedRows = loadResult == null ? null : loadResult.getV();
                log.info("IGinX CSV import finished. file={}, rows={}", csvPath, importedRows);
                return result;
            });
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.badRequest(ExceptionMessageUtils.buildDetailedMessage("导入 CSV 失败", ex), ex);
        }
    }

    /**
     * 组装 LOAD DATA FROM INFILE ... AS CSV SQL。
     *
     * @param csvFilePath CSV 文件绝对路径
     * @param targetPath 导入目标路径前缀
     * @param keyColumn KEY 列名；为空时走自动生成 KEY
     * @return 完整 SQL
     */
    public String buildLoadDataFromCsvSql(String csvFilePath, String targetPath, String keyColumn) {
        String normalizedFilePath = normalizeRequiredText(csvFilePath, "导入文件路径不能为空");
        String normalizedTargetPath = normalizeTargetPath(targetPath);
        StringBuilder sql = new StringBuilder();
        sql.append("LOAD DATA FROM INFILE \"")
            .append(escapeDoubleQuotedLiteral(normalizedFilePath))
            .append("\" AS CSV INTO ")
            .append(normalizedTargetPath);
        if (StringUtils.hasText(keyColumn)) {
            sql.append(" SET KEY \"")
                .append(escapeDoubleQuotedLiteral(keyColumn.trim()))
                .append("\"");
        }
        sql.append(";");
        return sql.toString();
    }

    /**
     * 保证 Session 已初始化。
     */
    private void ensureSessionAvailable() {
        if (session == null) {
            connect(true);
        }
    }

    /**
     * 获取可用 Session，不可用时抛出业务异常。
     *
     * @return 可用 Session
     */
    private Session requireSession() {
        ensureSessionAvailable();
        Session current = this.session;
        if (current == null) {
            throw BizException.internal(IGINX_UNAVAILABLE_MESSAGE);
        }
        return current;
    }

    /**
     * 校验并规范化目标路径，避免非法段导致 SQL 拼接错误。
     *
     * @param targetPath 原始路径
     * @return 规范化后的路径
     */
    private String normalizeTargetPath(String targetPath) {
        String normalized = normalizeRequiredText(targetPath, "导入目标路径不能为空");
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            throw BizException.badRequest("导入目标路径不能为空");
        }
        String[] parts = normalized.split("\\.");
        List<String> normalizedParts = new ArrayList<>();
        for (String part : parts) {
            String segment = part == null ? "" : part.trim();
            if (segment.isEmpty()) {
                throw BizException.badRequest("导入目标路径不合法: " + targetPath);
            }
            normalizedParts.add(quotePathSegment(segment));
        }
        return String.join(".", normalizedParts);
    }

    /**
     * 校验并规范化必填文本。
     *
     * @param text 原始文本
     * @param message 校验失败提示
     * @return 去空白后的文本
     */
    private String normalizeRequiredText(String text, String message) {
        if (!StringUtils.hasText(text)) {
            throw BizException.badRequest(message);
        }
        return text.trim();
    }

    /**
     * 规范化并校验 CSV 文件路径。
     *
     * @param csvFilePath 原始路径
     * @return 规范化后的绝对路径
     */
    private Path normalizeCsvFilePath(String csvFilePath) {
        String normalized = normalizeRequiredText(csvFilePath, "导入文件路径不能为空");
        final Path path;
        try {
            path = Path.of(normalized).toAbsolutePath().normalize();
        } catch (InvalidPathException ex) {
            throw BizException.badRequest("导入文件路径不合法: " + normalized);
        }
        if (!Files.exists(path)) {
            throw BizException.badRequest("导入文件不存在: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw BizException.badRequest("导入文件不是普通文件: " + path);
        }
        return path;
    }

    /**
     * 以分片方式上传 CSV 文件，匹配 IGinX 客户端的 LOAD DATA 执行流程。
     *
     * @param current 当前会话
     * @param csvPath 本地 CSV 路径
     * @param uploadFileName 服务端暂存文件名
     * @throws Exception 上传异常
     */
    private void uploadCsvFileChunks(Session current, Path csvPath, String uploadFileName) throws Exception {
        long offset = 0L;
        byte[] buffer = new byte[LOAD_CSV_CHUNK_SIZE];
        try (var inputStream = Files.newInputStream(csvPath)) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                byte[] chunkBytes = buffer;
                if (read < buffer.length) {
                    chunkBytes = new byte[read];
                    System.arraycopy(buffer, 0, chunkBytes, 0, read);
                }
                FileChunk chunk = new FileChunk(uploadFileName, offset, ByteBuffer.wrap(chunkBytes), read);
                current.uploadFileChunk(chunk);
                offset += read;
            }
        }
    }

    /**
     * 规范化路径段：普通标识符直出，其他字符使用反引号包裹。
     *
     * @param segment 路径段
     * @return 处理后的路径段
     */
    private String quotePathSegment(String segment) {
        String raw = segment;
        if (raw.startsWith("`") && raw.endsWith("`") && raw.length() >= 2) {
            raw = raw.substring(1, raw.length() - 1);
        }
        if (raw.matches("[A-Za-z0-9_]+")) {
            return raw;
        }
        return "`" + raw.replace("\\", "\\\\").replace("`", "\\`") + "`";
    }

    /**
     * 转义双引号字符串字面量中的特殊字符。
     *
     * @param value 原始值
     * @return 转义后字符串
     */
    private String escapeDoubleQuotedLiteral(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 建立 IGinX 连接。
     *
     * @param throwOnFailure 连接失败时是否抛出异常
     */
    private void connect(boolean throwOnFailure) {
        synchronized (this) {
            closeSilently();
            Session newSession = new Session(config.getHost(), config.getPort(), config.getUser(), config.getPassword());
            try {
                newSession.openSession();
                this.session = newSession;
                log.info("Connected to IGinX at {}:{}", config.getHost(), config.getPort());
            } catch (Exception e) {
                log.error("Failed to connect to IGinX", e);
                this.session = null;
                if (throwOnFailure) {
                    throw toBizException(e);
                }
            }
        }
    }

    /**
     * 判断是否为重复注册存储引擎错误。
     *
     * @param sql 执行 SQL
     * @param ex 异常
     * @return 是否重复注册
     */
    private boolean isDuplicateStorageEngineError(String sql, Exception ex) {
        if (sql == null) {
            return false;
        }
        String normalizedSql = sql.trim().toUpperCase(Locale.ROOT);
        if (!normalizedSql.startsWith("ADD STORAGEENGINE")) {
            return false;
        }
        String message = extractMessage(ex);
        if (message == null) {
            return false;
        }
        return message.toLowerCase(Locale.ROOT).contains("repeatedly add storage engine");
    }

    /**
     * 向上遍历异常链提取可用的错误信息。
     *
     * @param ex 异常
     * @return 错误信息或 null
     */
    private String extractMessage(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * 将异常转换为业务异常并统一错误语义。
     *
     * @param ex 异常
     * @return 业务异常
     */
    private BizException toBizException(Exception ex) {
        String message = extractMessage(ex);
        if (message != null) {
            String trimmed = message.trim();
            if (!trimmed.isBlank()) {
                if (isConnectionIssue(trimmed)) {
                    return BizException.internal(IGINX_UNAVAILABLE_MESSAGE, ex);
                }
                return BizException.badRequest(ExceptionMessageUtils.extractReadableMessage(ex), ex);
            }
        }
        // 某些异常（如 UnsupportedOperationException）可能没有 message，
        // 此时根据异常链判定是否连接问题，避免误报为“服务不可用”。
        if (isConnectionIssue(ex)) {
            return BizException.internal(IGINX_UNAVAILABLE_MESSAGE, ex);
        }
        return BizException.badRequest(ExceptionMessageUtils.buildDetailedMessage("IGinX 请求执行失败", ex), ex);
    }

    /**
     * 判断错误是否与连接问题相关。
     *
     * @param message 错误信息
     * @return 是否连接类错误
     */
    private boolean isConnectionIssue(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("transportexception")
            || lower.contains("connection refused")
            || lower.contains("connect failed")
            || lower.contains("timed out")
            || lower.contains("timeout")
            || lower.contains("no route")
            || lower.contains("socket");
    }

    /**
     * 判断异常链是否属于连接层问题。
     *
     * @param ex 异常
     * @return 是否连接类错误
     */
    private boolean isConnectionIssue(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && isConnectionIssue(message)) {
                return true;
            }
            String className = current.getClass().getName().toLowerCase(Locale.ROOT);
            if (className.contains("transportexception")
                || className.contains("socket")
                || className.contains("connectexception")
                || className.contains("sockettimeoutexception")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 判断是否需要重试。
     *
     * @param ex 异常
     * @return 是否重试
     */
    private boolean shouldRetry(Exception ex) {
        // 仅在明确识别为连接层问题时才重试，避免业务异常被误判后重连。
        return isConnectionIssue(ex);
    }

    /**
     * 提取安全的错误消息用于日志输出。
     *
     * @param ex 异常
     * @return 可读消息
     */
    private String safeMessage(Exception ex) {
        String message = extractMessage(ex);
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.trim();
    }

    /**
     * 静默关闭会话，避免关闭失败影响后续逻辑。
     */
    private void closeSilently() {
        if (this.session != null) {
            try {
                this.session.closeSession();
            } catch (Exception ignored) {
            } finally {
                this.session = null;
            }
        }
    }

    @FunctionalInterface
    /**
     * 会话执行器接口，用于封装会话级操作。
     *
     * @param <T> 返回类型
     */
    public interface SessionExecutor<T> {
        /**
         * 在给定会话中执行逻辑。
         *
         * @param session IGinX 会话
         * @return 执行结果
         * @throws Exception 执行异常
         */
        T apply(Session session) throws Exception;
    }
}
