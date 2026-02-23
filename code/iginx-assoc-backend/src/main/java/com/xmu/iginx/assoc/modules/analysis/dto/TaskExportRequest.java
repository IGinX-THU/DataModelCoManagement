package com.xmu.iginx.assoc.modules.analysis.dto;

import lombok.Data;

@Data
public class TaskExportRequest {

    private boolean includeModel = true;

    private boolean includeInput = true;

    private boolean includeOutput = true;

    private String format = "CSV";
}
