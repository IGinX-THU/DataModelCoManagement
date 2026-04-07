package com.xmu.iginx.assoc.modules.taskchain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 任务链运行记录实体。
 */
@Getter
@Setter
@Entity
@Table(name = "task_chain_run")
public class TaskChainRunEntity {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "chain_id")
    private Long chainId;

    @Column(name = "run_name", length = 120)
    private String runName;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "chain_mode", nullable = false, length = 20)
    private String chainMode;

    @Column(name = "range_start")
    private LocalDateTime rangeStart;

    @Column(name = "range_end")
    private LocalDateTime rangeEnd;

    @Column(name = "scheduled_start_time")
    private LocalDateTime scheduledStartTime;

    @Column(name = "scheduled_end_time")
    private LocalDateTime scheduledEndTime;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "result_prefix", length = 255)
    private String resultPrefix;

    @Column(name = "run_snapshot", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String runSnapshot;

    @Column(name = "exec_log", columnDefinition = "text")
    private String execLog;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
