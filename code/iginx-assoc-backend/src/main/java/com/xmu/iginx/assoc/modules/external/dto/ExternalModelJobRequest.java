package com.xmu.iginx.assoc.modules.external.dto;

import com.xmu.iginx.assoc.modules.task.dto.TaskSubmitRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 外部模型任务请求。
 */
@Data
public class ExternalModelJobRequest {

    @NotNull(message = "规则ID不能为空")
    private Long ruleId;

    @Valid
    @NotNull(message = "时间范围不能为空")
    private TaskSubmitRequest.TimeRange timeRange;

    @Min(value = 1, message = "轮询间隔最小为1秒")
    @Max(value = 60, message = "轮询间隔最大为60秒")
    private Integer pollIntervalSeconds = 1;

    @Min(value = 1, message = "超时时间最小为1秒")
    @Max(value = 86400, message = "超时时间最大为86400秒")
    private Integer timeoutSeconds = 1800;
}
