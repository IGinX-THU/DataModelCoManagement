package com.xmu.iginx.assoc.modules.task.service.impl;

import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.common.exception.ExceptionMessageUtils;
import com.xmu.iginx.assoc.modules.task.config.TaskExecutionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 基于外部命令的模型执行器抽象基类。
 */
@RequiredArgsConstructor
public abstract class AbstractCommandTaskModelExecutor implements TaskModelExecutor {

    protected final TaskExecutionProperties taskExecutionProperties;

    /**
     * 运行外部进程并捕获日志。
     *
     * @param command 命令行
     * @param workDir 工作目录
     * @return 进程输出日志
     * @throws Exception 执行异常
     */
    protected String runCommand(List<String> command, Path workDir) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workDir.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();

        ExecutorService streamExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "task-model-log-reader");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<String> outputFuture = streamExecutor.submit(() ->
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
            );
            long timeoutSeconds = Math.max(1L, taskExecutionProperties.getTimeoutSeconds());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    destroyProcess(process);
                    throw new InterruptedException("任务执行被中止");
                }
                if (process.waitFor(500, TimeUnit.MILLISECONDS)) {
                    break;
                }
                if (System.nanoTime() > deadline) {
                    destroyProcess(process);
                    throw BizException.badRequest("模型执行超时，请检查函数逻辑或缩小输入数据范围");
                }
            }
            String output = outputFuture.get(3, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                String message = StringUtils.hasText(output) ? output.trim() : "模型进程返回非零状态";
                throw BizException.badRequest(message);
            }
            return output;
        } finally {
            streamExecutor.shutdownNow();
        }
    }

    /**
     * 创建任务临时目录。
     *
     * @param taskId 任务 ID
     * @return 临时目录
     */
    protected Path createTaskWorkDir(String taskId) {
        try {
            Path root = Path.of(taskExecutionProperties.getWorkDir()).toAbsolutePath().normalize();
            Files.createDirectories(root);
            Path taskDir = root.resolve(taskId);
            Files.createDirectories(taskDir);
            return taskDir;
        } catch (IOException ex) {
            throw BizException.internal(ExceptionMessageUtils.buildDetailedMessage("创建任务临时目录失败", ex), ex);
        }
    }

    /**
     * 写入 UTF-8 文本文件。
     *
     * @param file 文件路径
     * @param content 文本内容
     */
    protected void writeUtf8(Path file, String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw BizException.internal(ExceptionMessageUtils.buildDetailedMessage("写入任务临时文件失败", ex), ex);
        }
    }

    /**
     * 写入二进制文件。
     *
     * @param file 文件路径
     * @param bytes 文件内容
     */
    protected void writeBytes(Path file, byte[] bytes) {
        try {
            Files.write(file, bytes);
        } catch (IOException ex) {
            throw BizException.internal(ExceptionMessageUtils.buildDetailedMessage("写入模型临时文件失败", ex), ex);
        }
    }

    /**
     * 安全终止外部进程。
     */
    private void destroyProcess(Process process) {
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
