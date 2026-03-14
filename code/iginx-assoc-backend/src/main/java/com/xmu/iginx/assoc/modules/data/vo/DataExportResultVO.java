package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

/**
 * 数据导出结果视图对象。
 */
@Data
public class DataExportResultVO {

    private Long taskId;

    private String status;

    private String fileName;

    private String downloadUrl;
}
