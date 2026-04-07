package com.xmu.iginx.assoc.modules.task.model;

import lombok.Data;

import java.util.Map;

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

    /**
     * 每个输出参数实际写入的数据条数。
     * <p>
     * 任务链运行时会据此判断多个上游输出能否安全拼接到同一节点，
     * 避免出现数量不一致导致的链式执行失败。
     * </p>
     */
    private Map<String, Integer> outputValueCounts;
}
