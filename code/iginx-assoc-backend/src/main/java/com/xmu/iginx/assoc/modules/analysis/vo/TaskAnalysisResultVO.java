package com.xmu.iginx.assoc.modules.analysis.vo;

import lombok.Data;

import java.util.List;

/**
 * 任务分析结果视图对象。
 * <p>
 * 根据任务输入类型不同，返回两种展示数据：
 * 1. 时序任务：使用 series 字段，前端按折线图展示；
 * 2. 结构化任务：使用 structuredResult 字段，前端可按分页结果表或完整结果图表展示。
 * </p>
 */
@Data
public class TaskAnalysisResultVO {

    /**
     * 分析模式：TIME_SERIES / STRUCTURED。
     */
    private String analysisMode;

    /**
     * 是否使用相对时间，仅时序任务有效。
     */
    private boolean relative;

    /**
     * 时序结果集合。
     */
    private List<TaskSeriesVO> series;

    /**
     * 结构化结果。
     */
    private TaskStructuredResultVO structuredResult;
}
