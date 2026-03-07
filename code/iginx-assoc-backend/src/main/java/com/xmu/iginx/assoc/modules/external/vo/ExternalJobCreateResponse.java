package com.xmu.iginx.assoc.modules.external.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExternalJobCreateResponse {

    private String jobId;

    private String status;

    private LocalDateTime submitTime;
}
