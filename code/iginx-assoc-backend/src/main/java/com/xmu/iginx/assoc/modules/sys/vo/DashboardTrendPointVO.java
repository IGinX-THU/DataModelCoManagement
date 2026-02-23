package com.xmu.iginx.assoc.modules.sys.vo;

import lombok.Data;

@Data
public class DashboardTrendPointVO {

    private String date;

    private Long taskCount;

    private Double avgDurationSec;
}
