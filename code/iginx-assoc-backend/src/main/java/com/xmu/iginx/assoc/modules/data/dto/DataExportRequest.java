package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 数据导出请求。
 */
@Data
public class DataExportRequest {

    @NotBlank(message = "导出类型不能为空")
    private String type;

    @NotNull(message = "数据源不能为空")
    private Long sourceId;

    @NotBlank(message = "导出格式不能为空")
    private String format;

    private String layout;

    private List<String> paths;

    @Valid
    private TimeRangeDTO timeRange;

    private String schema;

    private String table;

    private List<String> columns;

    private List<StructuredQueryCondition> conditions;

    private String sql;

    private Boolean async;
}
