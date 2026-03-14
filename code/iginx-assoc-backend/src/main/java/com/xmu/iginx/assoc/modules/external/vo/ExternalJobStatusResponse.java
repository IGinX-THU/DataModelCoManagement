package com.xmu.iginx.assoc.modules.external.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部任务状态响应。
 */
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
