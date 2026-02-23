package com.xmu.iginx.assoc.modules.data.dto;

import lombok.Data;

@Data
public class StructuredQueryCondition {

    private String logic = "AND";

    private String field;

    private String op = "=";

    private String value;
}
