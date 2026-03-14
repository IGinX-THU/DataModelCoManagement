package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 结构化数据导入请求。
 */
@Data
public class StructuredImportRequest {

    @NotNull(message = "数据源不能为空")
    private Long sourceId;

    @NotBlank(message = "目标 schema 不能为空")
    private String schema;

    @NotBlank(message = "目标表不能为空")
    private String table;

    private boolean autoCreateTable = false;

    private String conflictStrategy = "update";

    private String fileType;

    private Integer sheetIndex = 0;

    private List<String> primaryKeys;
}
