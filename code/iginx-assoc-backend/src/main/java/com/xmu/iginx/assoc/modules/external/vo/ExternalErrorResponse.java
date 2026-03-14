package com.xmu.iginx.assoc.modules.external.vo;

import lombok.Data;

/**
 * 外部任务错误响应。
 */
@Data
public class ExternalErrorResponse {

    private String code;

    private String message;

    private String traceId;
}
