package com.xmu.iginx.assoc.modules.analysis.vo;

import lombok.Data;

import java.util.List;

@Data
public class TaskSeriesVO {

    private String taskId;
    private String label;
    private String type;
    private String unit;
    private boolean relative;
    private List<TaskSeriesPointVO> points;
}
