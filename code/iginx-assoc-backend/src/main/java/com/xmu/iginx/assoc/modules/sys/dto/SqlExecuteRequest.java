package com.xmu.iginx.assoc.modules.sys.dto;

import lombok.Data;

/**
 * SQL 执行请求。
 */
@Data
public class SqlExecuteRequest {
    private String sql;
    private Integer limit;
    private Boolean formatTime;
}
