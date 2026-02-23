package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

@Data
public class DataExportResultVO {

    private Long taskId;

    private String status;

    private String fileName;

    private String downloadUrl;
}
