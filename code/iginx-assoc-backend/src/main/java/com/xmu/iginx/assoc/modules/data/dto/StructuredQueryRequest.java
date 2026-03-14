package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 结构化查询请求。
 */
@Data
public class StructuredQueryRequest {

    @NotNull(message = "数据源不能为空")
    private Long sourceId;

    @NotBlank(message = "Schema 不能为空")
    private String schema;

    @NotBlank(message = "表名不能为空")
    private String table;

    private List<StructuredQueryCondition> conditions;

    private String orderBy;

    private String orderDirection = "ASC";

    @Min(value = 1, message = "页码最小为1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "分页大小最小为1")
    @Max(value = 500, message = "分页大小最大为500")
    private Integer pageSize = 50;
}
