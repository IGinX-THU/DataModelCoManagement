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

@Getter
@Setter
@Entity
@Table(name = "sys_data_resource")
public class DataResourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "conn_config", nullable = false)
    private String connConfig;

    @Column(name = "mount_path", length = 200)
    private String mountPath;

    @Column(name = "time_range", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String timeRange;

    @Column(length = 255)
    private String description;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
