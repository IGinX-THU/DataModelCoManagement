package com.xmu.iginx.assoc.modules.external.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExternalJobStatusResponse {

    private String jobId;

    private String jobType;

    private String status;

    private LocalDateTime submitTime;

    private LocalDateTime startTime;

    private LocalDateTime finishTime;

    private ExternalErrorResponse error;
}
