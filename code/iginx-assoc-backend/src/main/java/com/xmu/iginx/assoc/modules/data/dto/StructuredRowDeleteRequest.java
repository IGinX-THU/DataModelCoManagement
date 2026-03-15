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
     * 数据源 ID。
     */
    @NotNull(message = "数据源不能为空")
    private Long sourceId;

    /**
     * Schema 名称（可不含 rt 前缀）。
     */
    @NotBlank(message = "Schema 不能为空")
    private String schema;

    /**
     * 表名。
     */
    @NotBlank(message = "表名不能为空")
    private String table;

    /**
     * 主键条件（字段名 -> 值），用于定位要删除的行。
     */
    @NotNull(message = "主键条件不能为空")
    private Map<String, Object> keys;
}
