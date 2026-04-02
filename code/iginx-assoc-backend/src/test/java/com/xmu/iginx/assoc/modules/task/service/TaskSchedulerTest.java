package com.xmu.iginx.assoc.modules.task.service;

import com.xmu.iginx.assoc.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 任务调度器测试。
 */
class TaskSchedulerTest {

    private TaskScheduler taskScheduler;

    @BeforeEach
    void setUp() {
        taskScheduler = new TaskScheduler();
    }

    @AfterEach
    void tearDown() {
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
    }

    /**
     * 定时开始应在计划时间附近触发，而不是立即执行。
     */
    @Test
    void schedule_shouldRunAtPlannedTime() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        long startNanos = System.nanoTime();

        taskScheduler.schedule(
            "task-delay",
            latch::countDown,
            LocalDateTime.now().plus(Duration.ofMillis(200)),
            this::failWithBizException
        );

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        assertTrue(elapsedMillis >= 150, "任务触发时间过早，未按计划延迟执行");
    }

    /**
     * 若终止时间早于计划开始时间，任务应在开始前被取消。
     */
    @Test
    void scheduleDeadline_shouldCancelPendingTaskBeforeItStarts() throws Exception {
        CountDownLatch runLatch = new CountDownLatch(1);
        CountDownLatch timeoutLatch = new CountDownLatch(1);

        taskScheduler.schedule(
            "task-pending-timeout",
            runLatch::countDown,
            LocalDateTime.now().plus(Duration.ofMillis(600)),
            this::failWithBizException
        );
        taskScheduler.scheduleDeadline(
            "task-pending-timeout",
            LocalDateTime.now().plus(Duration.ofMillis(150)),
            () -> {
                taskScheduler.cancel("task-pending-timeout");
                timeoutLatch.countDown();
            }
        );

        assertTrue(timeoutLatch.await(1, TimeUnit.SECONDS));
        assertFalse(runLatch.await(800, TimeUnit.MILLISECONDS), "任务已经超时取消，但仍然被执行");
    }

    /**
     * 到达终止时间后，应向运行中的任务发送中断信号。
     */
    @Test
    void scheduleDeadline_shouldInterruptRunningTask() throws Exception {
        CountDownLatch startedLatch = new CountDownLatch(1);
        CountDownLatch interruptedLatch = new CountDownLatch(1);

        taskScheduler.submit("task-running-timeout", () -> {
            startedLatch.countDown();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
                interruptedLatch.countDown();
                Thread.currentThread().interrupt();
            }
        });

        assertTrue(startedLatch.await(1, TimeUnit.SECONDS));
        taskScheduler.scheduleDeadline(
            "task-running-timeout",
            LocalDateTime.now().plus(Duration.ofMillis(150)),
            () -> taskScheduler.cancel("task-running-timeout")
        );

        assertTrue(interruptedLatch.await(2, TimeUnit.SECONDS), "运行中任务未在终止时间到达后收到中断信号");
    }

    /**
     * 将业务异常转成断言失败，便于定位调度问题。
     */
    private void failWithBizException(BizException ex) {
        fail(ex == null ? "发生未知调度异常" : ex.getMessage());
    }
}
