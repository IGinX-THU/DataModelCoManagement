package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

/**
 * 数据导出结果视图对象。
 */
@Data
public class DataExportResultVO {

    /**
     * 导出任务 ID（异步导出时返回）。
     */
    private Long taskId;

    /**
     * 导出状态（PENDING/RUNNING/SUCCESS/FAILED）。
     */
    private String status;

    /**
     * 导出文件名。
     */
    private String fileName;

    /**
     * 下载地址。
     */
    private String downloadUrl;
}
