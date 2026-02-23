package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

@Data
public class TableColumnVO {

    private String name;

    private String type;

    private boolean primaryKey;

    private boolean nullable;
}
