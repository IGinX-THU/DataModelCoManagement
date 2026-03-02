package com.xmu.iginx.assoc.framework.iginx;

import cn.edu.tsinghua.iginx.session.Session;
import cn.edu.tsinghua.iginx.session.SessionExecuteSqlResult;
import com.xmu.iginx.assoc.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class IginxStorageWrapper {

    private static final String IGNX_UNAVAILABLE_MESSAGE = "IGinX service unavailable, please retry later.";

    private final IginxConfig config;
    private volatile Session session;

    @PostConstruct
    public void init() {
        connect(false);
    }

    public SessionExecuteSqlResult executeSql(String sql) {
        try {
            log.debug("Executing SQL: {}", sql);
            Session current = requireSession();
            return current.executeSql(sql);
        } catch (BizException e) {
            throw e;
        } catch (Exception firstEx) {
            if (isDuplicateStorageEngineError(sql, firstEx)) {
                log.warn("IGinX duplicate storage engine detected, skip registration. sql={}", sql);
                return new SessionExecuteSqlResult();
            }
            if (!shouldRetry(firstEx)) {
                log.warn("IGinX execution rejected without reconnect. sql={}, message={}", sql, safeMessage(firstEx));
                throw toBizException(firstEx);
            }
            log.warn("IGinX execution failed, try reconnect once. sql={}", sql, firstEx);
            session = null;
            Session retry = requireSession();
            try {
                return retry.executeSql(sql);
            } catch (Exception secondEx) {
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

    private void ensureSessionAvailable() {
        if (session == null) {
            connect(true);
        }
    }

    private Session requireSession() {
        ensureSessionAvailable();
        Session current = this.session;
        if (current == null) {
            throw BizException.internal(IGNX_UNAVAILABLE_MESSAGE);
        }
        return current;
    }

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

    private BizException toBizException(Exception ex) {
        String message = extractMessage(ex);
        if (message != null) {
            String trimmed = message.trim();
            if (!trimmed.isBlank()) {
                if (isConnectionIssue(trimmed)) {
                    return BizException.internal(IGNX_UNAVAILABLE_MESSAGE);
                }
                return BizException.badRequest(trimmed);
            }
        }
        return BizException.internal(IGNX_UNAVAILABLE_MESSAGE);
    }

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

    private boolean shouldRetry(Exception ex) {
        String message = extractMessage(ex);
        return message == null || message.isBlank() || isConnectionIssue(message);
    }

    private String safeMessage(Exception ex) {
        String message = extractMessage(ex);
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.trim();
    }

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
    public interface SessionExecutor<T> {
        T apply(Session session) throws Exception;
    }
}
