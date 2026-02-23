package com.xmu.iginx.assoc.modules.analysis.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class TaskCompareRequest {

    @NotEmpty(message = "任务ID列表不能为空")
    private List<String> taskIds;

    private String mode = "absolute";
}
