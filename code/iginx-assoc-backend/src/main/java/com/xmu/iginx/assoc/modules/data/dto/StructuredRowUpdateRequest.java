package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 结构化行更新请求。
 */
@Data
public class StructuredRowUpdateRequest {

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
     * 行数据（包含主键字段与待更新字段）。
     */
    @NotNull(message = "数据不能为空")
    private Map<String, Object> data;
}
