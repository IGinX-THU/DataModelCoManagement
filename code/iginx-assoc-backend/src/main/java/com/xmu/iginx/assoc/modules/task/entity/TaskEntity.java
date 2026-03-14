package com.xmu.iginx.assoc.modules.task.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 任务实体。
 */
@Getter
@Setter
@Entity
@Table(name = "task")
public class TaskEntity {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "range_start")
    private LocalDateTime rangeStart;

    @Column(name = "range_end")
    private LocalDateTime rangeEnd;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "result_link", length = 255)
    private String resultLink;

    @Column(name = "exec_log", columnDefinition = "text")
    private String execLog;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
