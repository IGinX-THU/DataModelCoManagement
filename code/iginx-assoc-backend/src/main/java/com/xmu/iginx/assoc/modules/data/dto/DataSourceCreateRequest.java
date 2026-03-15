package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 数据源创建请求。
 */
@Data
public class DataSourceCreateRequest {

    /**
     * 数据源名称（唯一性由后端校验）。
     */
    @NotBlank(message = "数据源名称不能为空")
    private String name;

    /**
     * 数据源类型（INFLUXDB/IOTDB/POSTGRESQL）。
     */
    @NotBlank(message = "数据源类型不能为空")
    private String sourceType;

    /**
     * 数据源描述信息（可选）。
     */
    private String description;

    /**
     * 连接配置（主机、端口、账号等）。
     */
    @Valid
    private DataSourceConnectionConfig connectionConfig;
}

