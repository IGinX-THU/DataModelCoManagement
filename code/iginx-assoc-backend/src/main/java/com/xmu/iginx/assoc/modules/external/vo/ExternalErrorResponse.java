package com.xmu.iginx.assoc.modules.external.vo;

import lombok.Data;

@Data
public class ExternalErrorResponse {

    private String code;

    private String message;

    private String traceId;
}
