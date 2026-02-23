package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

@Data
public class DataSourceConnectionConfigVO {

    private String host;

    private Integer port;

    private String database;

    private String username;

    private String passwordMasked;

    private String extra;
}
