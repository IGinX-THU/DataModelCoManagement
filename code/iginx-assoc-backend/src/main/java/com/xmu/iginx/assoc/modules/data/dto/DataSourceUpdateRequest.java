package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 数据源更新请求。
 */
@Data
public class DataSourceUpdateRequest {

    @NotBlank(message = "数据源名称不能为空")
    private String name;

    private String description;

    @Valid
    private DataSourceConnectionConfig connectionConfig;
}
