package com.xmu.iginx.assoc.modules.sys.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.xmu.iginx.assoc.modules.sys.vo.SystemLogEntryVO;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 系统日志缓冲区，捕获并保存最近的日志记录。
 */
@Component
public class SystemLogBuffer {

    private static final int MAX_LOG_SIZE = 2000;
    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // 系统日志内存缓冲，保留最近 MAX_LOG_SIZE 条记录
    private final Object lock = new Object();
    private final Deque<SystemLogEntryVO> buffer = new ArrayDeque<>();
    private final AtomicLong sequence = new AtomicLong();

    /**
     * 初始化日志缓冲并挂载到根 Logger。
     */
    @PostConstruct
    public void init() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        LogBufferAppender appender = new LogBufferAppender(this);
        appender.setContext(context);
        appender.setName("SYSTEM_LOG_BUFFER");
        appender.start();
        rootLogger.addAppender(appender);
        appendInternal(Level.INFO, "SystemLogBuffer", "系统日志缓冲已启用");
    }

    /**
     * 获取日志快照。
     *
     * @param limit 最大返回数量
     * @param level 日志级别过滤
     * @param keyword 关键字过滤
     * @return 日志列表
     */
    public List<SystemLogEntryVO> snapshot(int limit, String level, String keyword) {
        String normalizedLevel = normalize(level);
        String normalizedKeyword = normalizeKeyword(keyword);
        List<SystemLogEntryVO> result = new ArrayList<>();
        synchronized (lock) {
            for (SystemLogEntryVO entry : buffer) {
                if (!matchLevel(entry, normalizedLevel)) {
                    continue;
                }
                if (!matchKeyword(entry, normalizedKeyword)) {
                    continue;
                }
                result.add(entry);
            }
        }
        if (result.size() <= limit) {
            return result;
        }
        // 只保留最近的 limit 条记录
        return new ArrayList<>(result.subList(result.size() - limit, result.size()));
    }

    /**
     * 追加日志事件到内存缓冲。
     *
     * @param event 日志事件
     */
    void appendEvent(ILoggingEvent event) {
        if (event == null) {
            return;
        }
        SystemLogEntryVO entry = buildEntry(event.getTimeStamp(),
            event.getLevel(),
            event.getLoggerName(),
            event.getFormattedMessage());
        synchronized (lock) {
            buffer.addLast(entry);
            while (buffer.size() > MAX_LOG_SIZE) {
                buffer.pollFirst();
            }
        }
    }

    /**
     * 写入系统内部日志到缓冲区。
     */
    private void appendInternal(Level level, String component, String message) {
        SystemLogEntryVO entry = buildEntry(System.currentTimeMillis(), level, component, message);
        synchronized (lock) {
            buffer.addLast(entry);
            while (buffer.size() > MAX_LOG_SIZE) {
                buffer.pollFirst();
            }
        }
    }

    /**
     * 构建日志条目对象。
     */
    private SystemLogEntryVO buildEntry(long timestamp, Level level, String component, String message) {
        SystemLogEntryVO entry = new SystemLogEntryVO();
        entry.setId(timestamp + "-" + sequence.incrementAndGet());
        entry.setTime(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(TIME_FORMATTER));
        entry.setLevel(level == null ? "INFO" : level.toString());
        entry.setComponent(StringUtils.hasText(component) ? component : "unknown");
        entry.setMessage(StringUtils.hasText(message) ? message : "");
        return entry;
    }

    /**
     * 日志级别过滤匹配。
     */
    private boolean matchLevel(SystemLogEntryVO entry, String level) {
        if (!StringUtils.hasText(level)) {
            return true;
        }
        return level.equalsIgnoreCase(entry.getLevel());
    }

    /**
     * 关键字过滤匹配。
     */
    private boolean matchKeyword(SystemLogEntryVO entry, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String message = entry.getMessage() == null ? "" : entry.getMessage().toLowerCase(Locale.ROOT);
        String component = entry.getComponent() == null ? "" : entry.getComponent().toLowerCase(Locale.ROOT);
        return message.contains(keyword) || component.contains(keyword);
    }

    /**
     * 规范化日志级别。
     */
    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 规范化关键字。
     */
    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    private static class LogBufferAppender extends AppenderBase<ILoggingEvent> {

        private final SystemLogBuffer buffer;

        private LogBufferAppender(SystemLogBuffer buffer) {
            this.buffer = buffer;
        }

        /**
         * 收集日志事件并写入缓冲区。
         */
        @Override
        protected void append(ILoggingEvent eventObject) {
            buffer.appendEvent(eventObject);
        }
    }
}
