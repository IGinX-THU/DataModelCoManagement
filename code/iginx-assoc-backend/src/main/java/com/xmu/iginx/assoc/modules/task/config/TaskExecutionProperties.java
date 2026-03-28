package com.xmu.iginx.assoc.modules.task.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 任务执行配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "assoc.task")
public class TaskExecutionProperties {

    /**
     * Python 可执行文件。
     */
    private String pythonExecutable = "python";

    /**
     * MATLAB 可执行文件。
     */
    private String matlabExecutable = "matlab";

    /**
     * 模型执行超时时间（秒）。
     */
    private long timeoutSeconds = 300L;

    /**
     * 任务执行临时目录。
     */
    private String workDir = System.getProperty("java.io.tmpdir") + "/iginx-assoc-task";
}
