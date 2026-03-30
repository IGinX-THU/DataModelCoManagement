package com.xmu.iginx.assoc.modules.task.entity;

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

    @Column(name = "scheduled_start_time")
    private LocalDateTime scheduledStartTime;

    @Column(name = "scheduled_end_time")
    private LocalDateTime scheduledEndTime;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "result_link", length = 255)
    private String resultLink;

    /**
     * 任务执行快照，持久化本次任务的输入/输出绑定、函数名、模型类型与实际输出路径。
     * <p>
     * 这样即使后续关联规则被修改，历史任务仍然可以按当时的真实执行上下文进行追溯、分析与导出。
     * </p>
     */
    @Column(name = "execution_snapshot", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String executionSnapshot;

    @Column(name = "exec_log", columnDefinition = "text")
    private String execLog;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
