package com.xmu.iginx.assoc.modules.relation.entity;

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
 * 关联规则实体。
 */
@Getter
@Setter
@Entity
@Table(name = "association_rule")
public class AssociationRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "model_id")
    private Long modelId;

    @Column(name = "function_name", length = 120)
    private String functionName;

    @Column(name = "output_target", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String outputTarget;

    @Column(name = "mapping_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String mappingJson;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
