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

    private final ThreadPoolExecutor executor;
    private final Map<String, Future<?>> futures = new ConcurrentHashMap<>();

    public TaskScheduler() {
        int coreSize = Math.max(2, Runtime.getRuntime().availableProcessors());
        this.executor = new ThreadPoolExecutor(
            coreSize,
            coreSize,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100)
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
            Future<?> future = executor.submit(runnable);
            futures.put(taskId, future);
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
        Future<?> future = futures.remove(taskId);
        if (future == null) {
            return false;
        }
        return future.cancel(true);
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
     * 容器关闭时释放线程池。
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
