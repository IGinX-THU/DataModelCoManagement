package com.xmu.iginx.assoc.modules.analysis.dto;

import lombok.Data;

/**
 * 任务报告生成请求。
 */
@Data
public class TaskReportRequest {

    /**
     * 是否包含统计摘要。
     */
    private boolean includeStats = true;

    /**
     * 是否包含图表分析。
     */
    private boolean includeCharts = true;

    /**
     * 数据预览策略：HEAD / UNIFORM。
     */
    private String previewStrategy = "HEAD";

    /**
     * 数据预览条数。
     */
    private Integer previewRows = 20;
}
