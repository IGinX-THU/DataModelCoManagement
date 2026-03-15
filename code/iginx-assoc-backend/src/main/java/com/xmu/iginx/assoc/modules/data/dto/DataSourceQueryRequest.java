package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 数据源查询请求。
 */
@Data
public class DataSourceQueryRequest {

    /**
     * 数据源名称（模糊匹配）。
     */
    private String name;

    /**
     * 数据源类型筛选条件。
     */
    private String sourceType;

    /**
     * 页码（从 1 开始）。
     */
    @Min(value = 1, message = "页码最小为1")
    private Integer pageNum = 1;

    /**
     * 分页大小。
     */
    @Min(value = 1, message = "分页大小最小为1")
    @Max(value = 100, message = "分页大小最大为100")
    private Integer pageSize = 10;
}
