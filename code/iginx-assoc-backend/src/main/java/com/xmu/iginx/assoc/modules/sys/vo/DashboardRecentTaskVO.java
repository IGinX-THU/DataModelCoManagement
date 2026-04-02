package com.xmu.iginx.assoc.modules.sys.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 仪表盘最近任务视图对象。
 */
@Data
public class DashboardRecentTaskVO {

    private String id;
    private String taskName;

    private String ruleName;

    private String modelName;

    private String modelType;

    private String status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createTime;

    private Long durationSec;
}
