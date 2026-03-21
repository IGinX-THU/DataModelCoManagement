package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

/**
 * 结构化表字段视图对象。
 */
@Data
public class StructuredSchemaColumnVO {

    /**
     * 字段名。
     */
    private String name;

    /**
     * 字段类型（IGinX DataType 枚举名）。
     */
    private String type;
}
