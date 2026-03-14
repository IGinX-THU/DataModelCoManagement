package com.xmu.iginx.assoc.modules.data.dto;

import lombok.Data;

/**
 * 结构化查询条件。
 */
@Data
public class StructuredQueryCondition {

    private String logic = "AND";

    private String field;

    private String op = "=";

    private String value;
}
