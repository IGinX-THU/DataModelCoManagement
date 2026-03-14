package com.xmu.iginx.assoc.modules.external.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部任务结果响应。
 */
@Data
public class ExternalJobResultResponse {

    private String jobId;

    private String status;

    private Object result;

    private String downloadUrl;

    private LocalDateTime finishTime;

    private ExternalErrorResponse error;
}
