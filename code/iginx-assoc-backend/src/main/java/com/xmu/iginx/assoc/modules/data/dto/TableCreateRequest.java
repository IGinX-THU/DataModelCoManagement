package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 建表请求。
 */
@Data
public class TableCreateRequest {

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
     * 字段定义列表。
     */
    @Valid
    private List<TableColumnDefinitionDTO> columns;

    /**
     * 主键字段列表（可选，用于逻辑去重或后续校验）。
     */
    private List<String> primaryKeys;
}
