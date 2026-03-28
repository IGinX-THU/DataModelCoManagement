package com.xmu.iginx.assoc.modules.task.model;

import lombok.Data;

/**
 * 任务执行结果摘要。
 */
@Data
public class TaskExecutionOutcome {

    /**
     * 执行日志。
     */
    private String execLog;

    /**
     * 成功写回的输出参数数量。
     */
    private int writtenOutputCount;
}
