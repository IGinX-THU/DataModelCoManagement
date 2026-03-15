package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

import java.util.List;

/**
 * 时序查询结果视图对象。
 */
@Data
public class TimeSeriesQueryResultVO {

    /**
     * 时间戳列表（毫秒）。
     */
    private List<Long> timestamps;

    /**
     * 序列数据列表。
     */
    private List<TimeSeriesSeriesVO> series;
}
