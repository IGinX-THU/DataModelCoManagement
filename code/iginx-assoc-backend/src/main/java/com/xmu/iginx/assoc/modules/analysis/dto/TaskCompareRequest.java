package com.xmu.iginx.assoc.modules.analysis.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 任务对比请求。
 */
@Data
public class TaskCompareRequest {

    @NotEmpty(message = "任务ID列表不能为空")
    private List<String> taskIds;

    private String mode = "absolute";

    /**
     * 是否启用降采样。
     */
    private boolean downsample = true;

    /**
     * 降采样聚合器，默认使用均值。
     */
    private String aggregator = "AVG";

    /**
     * 降采样步长（毫秒），为空时按任务时间跨度自动估算。
     */
    private Long precisionMs;
}
