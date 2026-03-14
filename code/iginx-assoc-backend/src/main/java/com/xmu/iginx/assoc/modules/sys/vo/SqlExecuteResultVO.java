package com.xmu.iginx.assoc.modules.sys.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * SQL 执行结果视图对象。
 */
@Data
public class SqlExecuteResultVO {
    private String sqlType;
    private String message;
    private Long executionTimeMs;
    private List<String> columns;
    private List<Map<String, Object>> rows;
}
