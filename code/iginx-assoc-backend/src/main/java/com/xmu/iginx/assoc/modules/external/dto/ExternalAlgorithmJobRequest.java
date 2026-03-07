package com.xmu.iginx.assoc.modules.external.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

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
