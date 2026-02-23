package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DataSourceVO {

    private Long id;

    private String name;

    private String sourceType;

    private String mountPath;

    private String description;

    private LocalDateTime createTime;

    private DataSourceConnectionConfigVO connectionConfig;
}
