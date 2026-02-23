package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TableColumnDefinitionDTO {

    @NotBlank(message = "字段名不能为空")
    private String name;

    @NotBlank(message = "字段类型不能为空")
    private String type;

    private boolean nullable = true;
}
