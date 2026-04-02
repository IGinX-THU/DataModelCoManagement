package com.xmu.iginx.assoc.modules.task.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务提交请求。
 */
@Data
public class TaskSubmitRequest {

    @NotNull(message = "规则ID不能为空")
    private Long ruleId;

    /**
     * 任务名称。
     * <p>
     * 允许用户手动命名；为空时由后端按规则名称和提交时间自动生成默认名称。
     * </p>
     */
    @Size(max = 120, message = "任务名称长度不能超过120个字符")
    private String taskName;

    /**
     * 计划开始执行时间。
     * <p>
     * 为空时表示任务提交后立即执行。
     * </p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime scheduledStartTime;

    /**
     * 计划终止时间。
     * <p>
     * 为空时表示不限制任务最晚终止时间。
     * </p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime scheduledEndTime;

    /**
     * 时间范围参数：
     * 1. 当规则输入包含 ts.* 时必填；
     * 2. 当规则输入全部为 rt.* 时可为空（表示直接按当前绑定数据执行）。
     */
    private TimeRange timeRange;

    /**
     * 时间范围参数。
     */
    @Data
    public static class TimeRange {
        @NotNull(message = "开始时间不能为空")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime start;

        @NotNull(message = "结束时间不能为空")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime end;
    }
}
