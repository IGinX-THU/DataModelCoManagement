package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 结构化行新增请求。
 */
@Data
public class StructuredRowCreateRequest {

    /**
     * 结构化表路径（IGinX 路径）。
     * <p>
     * 示例：rt.public.orders
     * </p>
     */
    @NotBlank(message = "路径不能为空")
    private String path;

    /**
     * 行数据（字段名 -> 值）。
     */
    @NotNull(message = "数据不能为空")
    private Map<String, Object> data;
}
