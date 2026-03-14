package com.xmu.iginx.assoc.modules.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 数据导出任务实体。
 */
@Getter
@Setter
@Entity
@Table(name = "data_export_task")
public class DataExportTaskEntity {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 数据源 ID */
    @Column(name = "source_id")
    private Long sourceId;

    /** 导出类型（时序/结构化） */
    @Column(name = "export_type", length = 20)
    private String exportType;

    /** 文件格式 */
    @Column(length = 20)
    private String format;

    /** 任务状态 */
    @Column(length = 20)
    private String status;

    /** 文件名 */
    @Column(name = "file_name", length = 255)
    private String fileName;

    /** 文件路径 */
    @Column(name = "file_path", length = 500)
    private String filePath;

    /** 导出请求 JSON */
    @Column(name = "request_json", columnDefinition = "text")
    private String requestJson;

    /** 错误信息 */
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    /** 创建时间 */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
