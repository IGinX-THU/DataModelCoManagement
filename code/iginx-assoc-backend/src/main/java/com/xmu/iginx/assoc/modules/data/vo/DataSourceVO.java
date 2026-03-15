package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源视图对象。
 */
@Data
public class DataSourceVO {

    /**
     * 数据源 ID。
     */
    private Long id;

    /**
     * 数据源名称。
     */
    private String name;

    /**
     * 数据源类型。
     */
    private String sourceType;

    /**
     * 数据源描述信息。
     */
    private String description;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 连接配置（脱敏展示）。
     */
    private DataSourceConnectionConfigVO connectionConfig;
}
