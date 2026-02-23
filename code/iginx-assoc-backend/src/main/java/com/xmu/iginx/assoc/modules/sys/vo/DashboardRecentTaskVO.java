package com.xmu.iginx.assoc.modules.sys.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DashboardRecentTaskVO {

    private String id;

    private String ruleName;

    private String modelName;

    private String modelType;

    private String status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createTime;

    private Long durationSec;
}
