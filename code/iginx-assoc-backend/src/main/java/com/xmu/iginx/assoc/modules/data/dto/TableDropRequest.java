package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 删表请求。
 */
@Data
public class TableDropRequest {

    @NotNull(message = "数据源不能为空")
    private Long sourceId;

    @NotBlank(message = "Schema 不能为空")
    private String schema;

    @NotBlank(message = "表名不能为空")
    private String table;
}
