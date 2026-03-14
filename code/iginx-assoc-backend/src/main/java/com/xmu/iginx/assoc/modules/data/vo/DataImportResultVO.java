package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

/**
 * 数据导入结果视图对象。
 */
@Data
public class DataImportResultVO {

    private long total;

    private long success;

    private long failed;

    private String errorFile;

    private String errorFileUrl;
}
