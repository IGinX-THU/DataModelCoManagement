package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 时间范围 DTO。
 */
@Data
public class TimeRangeDTO {

    /**
     * 开始时间（支持时间戳或可解析的时间字符串）。
     */
    @NotBlank(message = "开始时间不能为空")
    private String start;

    /**
     * 结束时间（支持时间戳或可解析的时间字符串）。
     */
    @NotBlank(message = "结束时间不能为空")
    private String end;
}
