package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

/**
 * 数据源连接配置视图对象（密码脱敏）。
 */
@Data
public class DataSourceConnectionConfigVO {

    private String host;

    private Integer port;

    private String database;

    private String username;

    private String passwordMasked;

    private Boolean hasData;

    private Boolean readOnly;

    private String extra;
}
