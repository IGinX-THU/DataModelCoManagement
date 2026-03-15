package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 时序删除请求。
 */
@Data
public class TimeSeriesDeleteRequest {

    /**
     * 数据源 ID。
     */
    @NotNull(message = "数据源不能为空")
    private Long sourceId;

    /**
     * 待删除的测点路径列表。
     */
    @NotEmpty(message = "测点路径不能为空")
    private List<String> paths;

    /**
     * 删除时间范围（为空表示全量删除）。
     */
    @Valid
    private TimeRangeDTO timeRange;

    /**
     * 操作类型（预留字段，默认 delete）。
     */
    private String operation = "delete";
}
