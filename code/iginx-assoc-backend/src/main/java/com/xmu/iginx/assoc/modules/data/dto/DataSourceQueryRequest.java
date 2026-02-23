package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class DataSourceQueryRequest {

    private String name;

    private String sourceType;

    @Min(value = 1, message = "页码最小为1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "分页大小最小为1")
    @Max(value = 100, message = "分页大小最大为100")
    private Integer pageSize = 10;
}
