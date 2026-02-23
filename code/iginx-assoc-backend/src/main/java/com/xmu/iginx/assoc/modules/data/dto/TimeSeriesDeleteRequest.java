package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class TimeSeriesDeleteRequest {

    @NotNull(message = "数据源不能为空")
    private Long sourceId;

    @NotEmpty(message = "测点路径不能为空")
    private List<String> paths;

    @Valid
    private TimeRangeDTO timeRange;

    private String operation = "delete";
}
