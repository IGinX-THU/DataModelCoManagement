package com.xmu.iginx.assoc.modules.task.service;

import com.xmu.iginx.assoc.common.exception.BizException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 任务调度器。
 * <p>
 * 这里继续使用轻量级线程池，原因是当前任务执行本质上是：
 * 1. 读取 IGinX 数据；
 * 2. 启动 Python / MATLAB 子进程；
 * 3. 将结果写回 IGinX。
 * </p>
 * <p>
 * 对于当前系统规模，这种方案足够直观且便于中止运行中的任务。
 * </p>
 */
@Component("assocTaskScheduler")
public class TaskScheduler {

    private static final int MAX_QUEUE_SIZE = 100;

    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService timerExecutor;
    private final Map<String, Future<?>> runningFutures = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> deadlineFutures = new ConcurrentHashMap<>();

    public TaskScheduler() {
        int coreSize = Math.max(2, Runtime.getRuntime().availableProcessors());
        this.executor = new ThreadPoolExecutor(
            coreSize,
            coreSize,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(MAX_QUEUE_SIZE)
        );
        this.timerExecutor = Executors.newScheduledThreadPool(
            Math.max(2, Math.min(4, coreSize)),
            runnable -> {
                Thread thread = new Thread(runnable, "assoc-task-timer");
                thread.setDaemon(true);
                return thread;
            }
        );
    }

    /**
     * 提交任务。
     *
     * @param taskId 任务 ID
     * @param runnable 任务逻辑
     */
    public void submit(String taskId, Runnable runnable) {
        try {
            Future<?> future = executor.submit(() -> {
                try {
                    runnable.run();
                } finally {
                    runningFutures.remove(taskId);
                }
            });
            runningFutures.put(taskId, future);
        } catch (RejectedExecutionException ex) {
            throw BizException.busy("系统繁忙，当前排队任务过多，请稍后重试。");
        }
    }

    /**
     * 定时提交任务。
     *
     * @param taskId 任务 ID
     * @param runnable 任务逻辑
     * @param scheduledStartTime 计划开始时间
     * @param submitFailureHandler 延迟提交失败后的回调
     */
    public void schedule(String taskId,
                         Runnable runnable,
                         LocalDateTime scheduledStartTime,
                         Consumer<BizException> submitFailureHandler) {
        ensureCapacity();
        long delayMillis = Math.max(0L, Duration.between(LocalDateTime.now(), scheduledStartTime).toMillis());
        try {
            ScheduledFuture<?> future = timerExecutor.schedule(() -> {
                scheduledFutures.remove(taskId);
                try {
                    submit(taskId, runnable);
                } catch (BizException ex) {
                    submitFailureHandler.accept(ex);
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
            scheduledFutures.put(taskId, future);
        } catch (RejectedExecutionException ex) {
            throw BizException.busy("系统繁忙，当前排队任务过多，请稍后重试。");
        }
    }

    /**
     * 注册任务终止时间。
     *
     * @param taskId 任务 ID
     * @param scheduledEndTime 计划终止时间
     * @param timeoutHandler 到时回调
     */
    public void scheduleDeadline(String taskId, LocalDateTime scheduledEndTime, Runnable timeoutHandler) {
        long delayMillis = Math.max(0L, Duration.between(LocalDateTime.now(), scheduledEndTime).toMillis());
        try {
            ScheduledFuture<?> future = timerExecutor.schedule(() -> {
                deadlineFutures.remove(taskId);
                timeoutHandler.run();
            }, delayMillis, TimeUnit.MILLISECONDS);
            ScheduledFuture<?> oldFuture = deadlineFutures.put(taskId, future);
            cancelFuture(oldFuture, false);
        } catch (RejectedExecutionException ex) {
            throw BizException.busy("系统繁忙，当前排队任务过多，请稍后重试。");
        }
    }

    /**
     * 取消任务。
     *
     * @param taskId 任务 ID
     * @return 是否成功发送取消信号
     */
    public boolean cancel(String taskId) {
        boolean cancelled = false;
        cancelled |= cancelFuture(scheduledFutures.remove(taskId), false);
        cancelled |= cancelFuture(runningFutures.remove(taskId), true);
        cancelled |= cancelFuture(deadlineFutures.remove(taskId), false);
        return cancelled;
    }

    /**
     * 清理任务句柄。
     *
     * @param taskId 任务 ID
     */
    public void clear(String taskId) {
        cancelFuture(scheduledFutures.remove(taskId), false);
        runningFutures.remove(taskId);
        cancelFuture(deadlineFutures.remove(taskId), false);
    }

    /**
     * 校验调度容量。
     */
    private void ensureCapacity() {
        int waitingCount = scheduledFutures.size() + runningFutures.size() + executor.getQueue().size();
        if (waitingCount >= MAX_QUEUE_SIZE + executor.getMaximumPoolSize()) {
            throw BizException.busy("系统繁忙，当前排队任务过多，请稍后重试。");
        }
    }

    /**
     * 安全取消 Future。
     */
    private boolean cancelFuture(Future<?> future, boolean mayInterruptIfRunning) {
        if (future == null) {
            return false;
        }
        return future.cancel(mayInterruptIfRunning);
    }

    /**
     * 容器关闭时释放线程池。
     */
    @PreDestroy
    public void shutdown() {
        timerExecutor.shutdownNow();
        executor.shutdownNow();
    }
}
