package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 测点操作请求。
 */
@Data
public class MeasurementRequest {

    @NotNull(message = "数据源不能为空")
    private Long sourceId;

    @NotBlank(message = "测点路径不能为空")
    private String path;

    @NotBlank(message = "数据类型不能为空")
    private String dataType;
}
