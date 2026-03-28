package com.xmu.iginx.assoc.modules.task.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务执行快照。
 * <p>
 * 在任务提交时生成，并与任务记录一同落库，确保：
 * 1. 运行前校验通过后的实际输入输出结构可追溯；
 * 2. 任务历史分析/导出不受后续规则修改影响；
 * 3. 默认输出路径 task.result.* 能在后续查询时被准确还原。
 * </p>
 */
@Data
public class TaskExecutionSnapshot {

    /**
     * 规则 ID。
     */
    private Long ruleId;

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
     * 本次执行调用的函数名。
     */
    private String functionName;

    /**
     * 默认结果前缀，例如 task.result.<taskId>。
     */
    private String defaultResultPrefix;

    /**
     * 是否需要时间区间。
     * <p>
     * 当输入中包含 ts.* 路径时，该值为 true。
     * </p>
     */
    private Boolean requiresTimeRange;

    /**
     * 本次任务使用的时间区间开始值，可为空。
     */
    private LocalDateTime rangeStart;

    /**
     * 本次任务使用的时间区间结束值，可为空。
     */
    private LocalDateTime rangeEnd;

    /**
     * 输入绑定列表。
     */
    private List<TaskExecutionBinding> inputs;

    /**
     * 输出绑定列表。
     */
    private List<TaskExecutionBinding> outputs;
}
