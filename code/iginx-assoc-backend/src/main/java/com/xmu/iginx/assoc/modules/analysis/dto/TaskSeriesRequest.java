package com.xmu.iginx.assoc.modules.analysis.dto;

import lombok.Data;

/**
 * 任务曲线查询请求。
 */
@Data
public class TaskSeriesRequest {

    /**
     * 是否按相对时间对齐展示。
     */
    private boolean relative = false;

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

    /**
     * 结构化结果页码（从 1 开始）。
     */
    private Integer pageNum = 1;

    /**
     * 结构化结果分页大小。
     */
    private Integer pageSize = 50;

    /**
     * 是否返回结构化结果的完整图表数据。
     */
    private boolean includeChartData = false;

    /**
     * 是否返回结构化结果分页数据。
     */
    private boolean includePageData = true;
}
