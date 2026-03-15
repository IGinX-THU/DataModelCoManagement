package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 数据源连接配置。
 */
@Data
public class DataSourceConnectionConfig {

    /**
     * 数据源主机地址（IP 或域名）。
     */
    @NotBlank(message = "主机地址不能为空")
    private String host;

    /**
     * 连接端口。
     */
    @NotNull(message = "端口不能为空")
    @Min(value = 0, message = "端口最小值为0")
    @Max(value = 65535, message = "端口最大值为65535")
    private Integer port;

    /**
     * 数据库/实例名称。
     */
    @NotBlank(message = "数据库名称不能为空")
    private String database;

    /**
     * 登录用户名。
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 登录密码（明文，后端会加密保存）。
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 是否已有历史数据（用于数据源初始化策略）。
     */
    @NotNull(message = "has_data 不能为空")
    private Boolean hasData;

    /**
     * 是否只读连接（只允许查询，不允许写入）。
     */
    @NotNull(message = "is_read_only 不能为空")
    private Boolean readOnly;

    /**
     * 扩展配置（可选，建议使用 JSON 字符串存储额外参数）。
     */
    private String extra;
}
