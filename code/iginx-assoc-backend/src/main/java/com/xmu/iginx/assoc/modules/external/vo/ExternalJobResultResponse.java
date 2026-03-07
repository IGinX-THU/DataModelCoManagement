package com.xmu.iginx.assoc.modules.external.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExternalJobResultResponse {

    private String jobId;

    private String status;

    private Object result;

    private String downloadUrl;

    private LocalDateTime finishTime;

    private ExternalErrorResponse error;
}
