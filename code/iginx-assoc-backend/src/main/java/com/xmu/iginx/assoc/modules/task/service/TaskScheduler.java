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

@Component("assocTaskScheduler")
public class TaskScheduler {

    private final ThreadPoolExecutor executor;
    private final Map<String, Future<?>> futures = new ConcurrentHashMap<>();

    public TaskScheduler() {
        int coreSize = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.executor = new ThreadPoolExecutor(
            coreSize,
            coreSize,
            60,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100)
        );
    }

    public void submit(String taskId, Runnable task) {
        try {
            Future<?> future = executor.submit(task);
            futures.put(taskId, future);
        } catch (RejectedExecutionException ex) {
            throw BizException.busy("系统繁忙，当前排队任务过多，请稍后重试。");
        }
    }

    public boolean cancel(String taskId) {
        Future<?> future = futures.remove(taskId);
        if (future != null) {
            return future.cancel(true);
        }
        return false;
    }

    public void clear(String taskId) {
        futures.remove(taskId);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
