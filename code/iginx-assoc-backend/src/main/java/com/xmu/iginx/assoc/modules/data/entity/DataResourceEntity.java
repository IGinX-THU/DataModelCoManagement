package com.xmu.iginx.assoc.modules.data.entity;

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
 * 数据源资源实体。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_data_resource")
public class DataResourceEntity {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 数据源名称 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 数据源类型 */
    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    /** 连接配置（加密后） */
    @Column(name = "conn_config", nullable = false)
    private String connConfig;

    /** 时间范围配置（JSONB） */
    @Column(name = "time_range", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String timeRange;

    /** 描述信息 */
    @Column(length = 255)
    private String description;

    /** 创建时间 */
    @Column(name = "create_time")
    private LocalDateTime createTime;
}
