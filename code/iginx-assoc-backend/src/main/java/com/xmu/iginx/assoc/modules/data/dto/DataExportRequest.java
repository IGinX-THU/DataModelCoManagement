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

    /**
     * 导出类型（时序/结构化），例如 TS、TIME_SERIES、STRUCT。
     */
    @NotBlank(message = "导出类型不能为空")
    private String type;

    /**
     * 数据源 ID。
     */
    @NotNull(message = "数据源不能为空")
    private Long sourceId;

    /**
     * 导出格式，例如 CSV、JSON、EXCEL。
     */
    @NotBlank(message = "导出格式不能为空")
    private String format;

    /**
     * 时序导出布局（wide/long），为空默认 wide。
     */
    private String layout;

    /**
     * 时序导出测点路径列表。
     */
    private List<String> paths;

    /**
     * 时序导出时间范围。
     */
    @Valid
    private TimeRangeDTO timeRange;

    /**
     * 结构化导出 schema。
     */
    private String schema;

    /**
     * 结构化导出表名。
     */
    private String table;

    /**
     * 结构化导出列（为空表示导出全部可见列）。
     */
    private List<String> columns;

    /**
     * 结构化导出查询条件列表。
     */
    private List<StructuredQueryCondition> conditions;

    /**
     * 自定义 SQL（仅支持 SELECT），优先级高于 schema/table/conditions。
     */
    private String sql;

    /**
     * 是否异步导出；为空时根据数据规模自动估算。
     */
    private Boolean async;
}
