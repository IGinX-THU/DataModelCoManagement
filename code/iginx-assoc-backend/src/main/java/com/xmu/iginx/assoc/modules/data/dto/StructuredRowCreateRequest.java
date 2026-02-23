package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class StructuredRowCreateRequest {

    @NotNull(message = "数据源不能为空")
    private Long sourceId;

    @NotBlank(message = "Schema 不能为空")
    private String schema;

    @NotBlank(message = "表名不能为空")
    private String table;

    @NotNull(message = "数据不能为空")
    private Map<String, Object> data;
}
