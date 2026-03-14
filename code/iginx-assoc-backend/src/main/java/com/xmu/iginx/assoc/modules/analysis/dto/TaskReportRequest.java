package com.xmu.iginx.assoc.modules.analysis.dto;

import lombok.Data;

/**
 * 任务报告生成请求。
 */
@Data
public class TaskReportRequest {

    private boolean includeStats = true;

    private boolean includeCharts = true;
}
