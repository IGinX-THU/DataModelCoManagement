package com.xmu.iginx.assoc.modules.sys.vo;

import lombok.Data;

import java.util.List;

/**
 * 仪表盘总览视图对象。
 */
@Data
public class DashboardSummaryVO {

    private long modelCount;

    private long ruleCount;

    private long dataSourceCount;

    private long taskCount;

    private long runningTaskCount;

    private long successTaskCount;

    private long failedTaskCount;

    private List<DashboardTrendPointVO> taskTrend;

    private List<DashboardRecentTaskVO> recentTasks;
}
