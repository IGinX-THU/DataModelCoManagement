package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 结构化查询请求。
 */
@Data
public class StructuredQueryRequest {

    /**
     * IGinX 结构化表路径（如 rt.public.device）。
     */
    @NotBlank(message = "IGinX 表路径不能为空")
    private String tablePath;

    /**
     * 查询条件列表。
     */
    private List<StructuredQueryCondition> conditions;

    /**
     * 排序字段。
     */
    private String orderBy;

    /**
     * 排序方向（ASC/DESC）。
     */
    private String orderDirection = "ASC";

    /**
     * 页码（从 1 开始）。
     */
    @Min(value = 1, message = "页码最小为1")
    private Integer pageNum = 1;

    /**
     * 分页大小。
     */
    @Min(value = 1, message = "分页大小最小为1")
    @Max(value = 500, message = "分页大小最大为500")
    private Integer pageSize = 50;
}
