package com.xmu.iginx.assoc.modules.taskchain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 任务链定义实体。
 */
@Getter
@Setter
@Entity
@Table(name = "task_chain")
public class TaskChainEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chain_name", nullable = false, length = 120)
    private String chainName;

    @Column(name = "chain_mode", nullable = false, length = 20)
    private String chainMode;

    @Column(name = "definition_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String definitionJson;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
