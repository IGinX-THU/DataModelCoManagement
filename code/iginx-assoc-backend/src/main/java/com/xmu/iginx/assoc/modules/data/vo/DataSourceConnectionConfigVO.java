package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

/**
 * 数据源连接配置视图对象（密码脱敏）。
 */
@Data
public class DataSourceConnectionConfigVO {

    /**
     * 主机地址。
     */
    private String host;

    /**
     * 端口号。
     */
    private Integer port;

    /**
     * 数据库/实例名称。
     */
    private String database;

    /**
     * 用户名。
     */
    private String username;

    /**
     * 脱敏后的密码展示（不返回明文）。
     */
    private String passwordMasked;

    /**
     * 是否已有历史数据。
     */
    private Boolean hasData;

    /**
     * 是否只读连接。
     */
    private Boolean readOnly;

    /**
     * 扩展配置（可选）。
     */
    private String extra;
}
