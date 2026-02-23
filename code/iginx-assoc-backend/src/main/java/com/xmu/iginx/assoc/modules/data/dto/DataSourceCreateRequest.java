package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DataSourceCreateRequest {

    @NotBlank(message = "数据源名称不能为空")
    private String name;

    @NotBlank(message = "数据源类型不能为空")
    private String sourceType;

    @NotBlank(message = "挂载别名不能为空")
    private String mountPath;

    private String description;

    @Valid
    private DataSourceConnectionConfig connectionConfig;
}
