package com.xmu.iginx.assoc.modules.task.model;

import lombok.Data;

/**
 * 任务执行计划。
 * <p>
 * 该对象在任务提交阶段生成，作为“运行前校验通过后的事实快照”，
 * 用于异步线程真正执行模型任务。
 * </p>
 */
@Data
public class TaskExecutionPlan {

    /**
     * 任务 ID。
     */
    private String taskId;

    /**
     * 规则 ID。
     */
    private Long ruleId;

    /**
     * 规则名称。
     */
    private String ruleName;

    /**
     * 模型版本 ID。
     */
    private Long modelId;

    /**
     * 模型版本号。
     */
    private String modelVersion;

    /**
     * 模型类型，例如 PY / MAT。
     */
    private String modelType;

    /**
     * 模型文件名，仅用于日志与临时文件命名。
     */
    private String modelFileName;

    /**
     * 模型存储路径。
     */
    private String modelStoragePath;

    /**
     * 模型文件大小。
     */
    private Long modelFileSize;

    /**
     * 本次任务执行快照。
     */
    private TaskExecutionSnapshot snapshot;
}
