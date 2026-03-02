package com.xmu.iginx.assoc.modules.sys.dto;

import lombok.Data;

@Data
public class SqlExecuteRequest {
    private String sql;
    private Integer limit;
    private Boolean formatTime;
}
