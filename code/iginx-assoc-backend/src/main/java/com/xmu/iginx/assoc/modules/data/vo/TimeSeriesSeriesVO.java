package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

import java.util.List;

@Data
public class TimeSeriesSeriesVO {

    private String path;

    private List<Object> values;
}
