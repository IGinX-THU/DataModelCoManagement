package com.xmu.iginx.assoc.modules.external.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部任务创建响应。
 */
@Data
public class ExternalJobCreateResponse {

    private String jobId;

    private String status;

    private LocalDateTime submitTime;
}
