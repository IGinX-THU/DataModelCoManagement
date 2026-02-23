package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

import java.util.List;

@Data
public class TimeSeriesQueryResultVO {

    private List<Long> timestamps;

    private List<TimeSeriesSeriesVO> series;
}
