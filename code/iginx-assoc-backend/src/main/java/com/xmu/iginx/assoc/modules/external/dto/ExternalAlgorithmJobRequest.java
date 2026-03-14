package com.xmu.iginx.assoc.modules.external.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 外部算法任务请求。
 */
@Data
public class ExternalAlgorithmJobRequest {

    @NotBlank(message = "算法动作不能为空")
    private String action;

    private String taskId;

    private Boolean relative;

    private List<String> taskIds;

    private String mode;

    private Boolean includeModel;

    private Boolean includeInput;

    private Boolean includeOutput;

    private String format;

    private Boolean includeStats;

    private Boolean includeCharts;
}
