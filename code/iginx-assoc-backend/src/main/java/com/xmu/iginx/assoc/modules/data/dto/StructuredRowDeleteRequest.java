package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 结构化行删除请求。
 */
@Data
public class StructuredRowDeleteRequest {

    /**
     * 结构化表路径（IGinX 路径）。
     * <p>
     * 示例：rt.public.orders
     * </p>
     */
    @NotBlank(message = "路径不能为空")
    private String path;

    /**
     * 主键条件（字段名 -> 值），用于定位要删除的行。
     */
    @NotNull(message = "主键条件不能为空")
    private Map<String, Object> keys;
}
