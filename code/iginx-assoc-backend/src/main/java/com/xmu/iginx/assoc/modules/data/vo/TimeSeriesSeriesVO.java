package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

import java.util.List;

/**
 * 时序序列视图对象。
 */
@Data
public class TimeSeriesSeriesVO {

    /**
     * 测点路径。
     */
    private String path;

    /**
     * 与时间戳对应的值列表。
     */
    private List<Object> values;
}
