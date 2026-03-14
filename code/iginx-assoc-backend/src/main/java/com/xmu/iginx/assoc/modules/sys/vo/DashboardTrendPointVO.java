package com.xmu.iginx.assoc.modules.sys.vo;

import lombok.Data;

/**
 * 仪表盘趋势点视图对象。
 */
@Data
public class DashboardTrendPointVO {

    private String date;

    private Long taskCount;

    private Double avgDurationSec;
}
