package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

import java.util.List;

/**
 * 时序查询结果视图对象。
 */
@Data
public class TimeSeriesQueryResultVO {

    private List<Long> timestamps;

    private List<TimeSeriesSeriesVO> series;
}
