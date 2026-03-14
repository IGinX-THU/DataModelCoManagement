package com.xmu.iginx.assoc.framework.iginx;

import cn.edu.tsinghua.iginx.session.Session;
import cn.edu.tsinghua.iginx.session.SessionExecuteSqlResult;
import com.xmu.iginx.assoc.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * IGinX 会话包装器，提供 SQL 执行与重试能力。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IginxStorageWrapper {

    private static final String IGINX_UNAVAILABLE_MESSAGE = "IGinX service unavailable, please retry later.";

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
                    return BizException.internal(IGINX_UNAVAILABLE_MESSAGE);
                }
                return BizException.badRequest(trimmed);
            }
        }
        return BizException.internal(IGINX_UNAVAILABLE_MESSAGE);
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
     * 判断是否需要重试。
     *
     * @param ex 异常
     * @return 是否重试
     */
    private boolean shouldRetry(Exception ex) {
        String message = extractMessage(ex);
        return message == null || message.isBlank() || isConnectionIssue(message);
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
