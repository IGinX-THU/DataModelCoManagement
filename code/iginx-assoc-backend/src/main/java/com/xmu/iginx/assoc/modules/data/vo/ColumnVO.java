package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

/**
 * SHOW COLUMNS 返回列信息。
 */
@Data
public class ColumnVO {

    /**
     * 测点路径。
     */
    private String path;

    /**
     * 数据类型。
     */
    private String dataType;
}
