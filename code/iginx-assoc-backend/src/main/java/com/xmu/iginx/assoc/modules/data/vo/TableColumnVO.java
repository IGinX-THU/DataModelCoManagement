package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

/**
 * 表字段视图对象。
 */
@Data
public class TableColumnVO {

    private String name;

    private String type;

    private boolean primaryKey;

    private boolean nullable;
}
