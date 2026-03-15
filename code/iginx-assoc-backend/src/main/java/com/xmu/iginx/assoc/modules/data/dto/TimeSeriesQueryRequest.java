package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 时序查询请求。
 */
@Data
public class TimeSeriesQueryRequest {

    /**
     * 数据源 ID。
     */
    @NotNull(message = "数据源不能为空")
    private Long sourceId;

    /**
     * 待查询的测点路径列表。
     */
    @NotEmpty(message = "测点路径不能为空")
    private List<String> paths;

    /**
     * 查询时间范围。
     */
    @Valid
    private TimeRangeDTO timeRange;

    /**
     * 是否启用降采样。
     */
    private boolean downsample = false;

    /**
     * 聚合器名称（如 AVG、MAX、MIN）。
     */
    private String aggregator = "AVG";

    /**
     * 时间精度（毫秒），用于降采样桶大小。
     */
    private Long precisionMs;
}
