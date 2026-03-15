package com.xmu.iginx.assoc.modules.data.dto;

import lombok.Data;

/**
 * 结构化查询条件。
 */
@Data
public class StructuredQueryCondition {

    /**
     * 条件之间的逻辑关系（AND/OR）。
     */
    private String logic = "AND";

    /**
     * 字段名（列名）。
     */
    private String field;

    /**
     * 操作符（=、>、<、LIKE 等）。
     */
    private String op = "=";

    /**
     * 条件值（按字符串传入，后端按字段类型解析）。
     */
    private String value;
}
