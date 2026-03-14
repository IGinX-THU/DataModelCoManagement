package com.xmu.iginx.assoc.modules.task.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务提交请求。
 */
@Data
public class TaskSubmitRequest {

    @NotNull(message = "规则ID不能为空")
    private Long ruleId;

    @NotNull(message = "时间范围不能为空")
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
