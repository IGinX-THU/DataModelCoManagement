package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 测点操作请求。
 */
@Data
public class MeasurementRequest {

    /**
     * 数据源 ID。
     */
    @NotNull(message = "数据源不能为空")
    private Long sourceId;

    /**
     * 测点路径（如 ts.root.device.sensor）。
     */
    @NotBlank(message = "测点路径不能为空")
    private String path;

    /**
     * 数据类型（Iginx 支持的类型字符串）。
     */
    @NotBlank(message = "数据类型不能为空")
    private String dataType;
}
