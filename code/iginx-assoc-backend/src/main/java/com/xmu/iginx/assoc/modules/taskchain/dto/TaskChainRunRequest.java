package com.xmu.iginx.assoc.modules.taskchain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务链运行请求。
 */
@Data
public class TaskChainRunRequest {

    @Size(max = 120, message = "运行名称长度不能超过120个字符")
    private String runName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime scheduledStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime scheduledEndTime;

    @Valid
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
