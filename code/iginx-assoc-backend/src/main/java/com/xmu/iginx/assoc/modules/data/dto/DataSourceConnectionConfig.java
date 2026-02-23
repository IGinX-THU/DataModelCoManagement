package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DataSourceConnectionConfig {

    @NotBlank(message = "主机地址不能为空")
    private String host;

    @NotNull(message = "端口不能为空")
    @Min(value = 0, message = "端口最小值为0")
    @Max(value = 65535, message = "端口最大值为65535")
    private Integer port;

    @NotBlank(message = "数据库名称不能为空")
    private String database;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String extra;
}
