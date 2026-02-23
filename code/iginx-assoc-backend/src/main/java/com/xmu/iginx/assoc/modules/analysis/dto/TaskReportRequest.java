package com.xmu.iginx.assoc.modules.analysis.dto;

import lombok.Data;

@Data
public class TaskReportRequest {

    private boolean includeStats = true;

    private boolean includeCharts = true;
}
