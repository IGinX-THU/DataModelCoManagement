package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 表字段定义 DTO。
 */
@Data
public class TableColumnDefinitionDTO {

    /**
     * 字段名。
     */
    @NotBlank(message = "字段名不能为空")
    private String name;

    /**
     * 字段类型（如 int、double、varchar 等）。
     */
    @NotBlank(message = "字段类型不能为空")
    private String type;

    /**
     * 是否允许为空。
     */
    private boolean nullable = true;
}
