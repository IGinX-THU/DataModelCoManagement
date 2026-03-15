package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

/**
 * 表字段视图对象。
 */
@Data
public class TableColumnVO {

    /**
     * 字段名。
     */
    private String name;

    /**
     * 字段类型（展示用）。
     */
    private String type;

    /**
     * 是否为主键。
     */
    private boolean primaryKey;

    /**
     * 是否可为空。
     */
    private boolean nullable;
}
