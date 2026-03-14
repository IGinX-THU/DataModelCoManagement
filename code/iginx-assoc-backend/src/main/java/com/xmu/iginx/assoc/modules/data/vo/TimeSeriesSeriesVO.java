package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

import java.util.List;

/**
 * 时序序列视图对象。
 */
@Data
public class TimeSeriesSeriesVO {

    private String path;

    private List<Object> values;
}
