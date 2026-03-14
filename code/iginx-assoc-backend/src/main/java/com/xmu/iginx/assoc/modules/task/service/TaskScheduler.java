package com.xmu.iginx.assoc.modules.task.service;

import com.xmu.iginx.assoc.common.exception.BizException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 简单任务调度器，基于线程池执行任务并维护任务句柄。
 */
@Component("assocTaskScheduler")
public class TaskScheduler {

    private final ThreadPoolExecutor executor;
    private final Map<String, Future<?>> futures = new ConcurrentHashMap<>();

    public TaskScheduler() {
        // 保留 1 个核心线程用于业务处理
        int coreSize = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.executor = new ThreadPoolExecutor(
            coreSize,
            coreSize,
            60,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100)
        );
    }

    /**
     * 提交任务到线程池。
     *
     * @param taskId 任务 ID
     * @param task 任务逻辑
     */
    public void submit(String taskId, Runnable task) {
        try {
            Future<?> future = executor.submit(task);
            futures.put(taskId, future);
        } catch (RejectedExecutionException ex) {
            throw BizException.busy("系统繁忙，当前排队任务过多，请稍后重试。");
        }
    }

    /**
     * 取消指定任务。
     *
     * @param taskId 任务 ID
     * @return 是否成功取消
     */
    public boolean cancel(String taskId) {
        Future<?> future = futures.remove(taskId);
        if (future != null) {
            return future.cancel(true);
        }
        return false;
    }

    /**
     * 清理任务句柄。
     *
     * @param taskId 任务 ID
     */
    public void clear(String taskId) {
        futures.remove(taskId);
    }

    /**
     * 关闭线程池。
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
