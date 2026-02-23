package com.xmu.iginx.assoc.modules.task.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskVO {

    private String id;
    private Long ruleId;
    private String status;
    private LocalDateTime rangeStart;
    private LocalDateTime rangeEnd;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String resultLink;
    private String execLog;
    private LocalDateTime createTime;
}
