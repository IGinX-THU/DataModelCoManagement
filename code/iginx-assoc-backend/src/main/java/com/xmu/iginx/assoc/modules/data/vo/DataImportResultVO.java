package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

/**
 * 数据导入结果视图对象。
 */
@Data
public class DataImportResultVO {

    /**
     * 导入记录总数。
     */
    private long total;

    /**
     * 导入成功数量。
     */
    private long success;

    /**
     * 导入失败数量。
     */
    private long failed;

    /**
     * 失败明细文件名（可选）。
     */
    private String errorFile;

    /**
     * 失败明细文件下载地址。
     */
    private String errorFileUrl;
}
