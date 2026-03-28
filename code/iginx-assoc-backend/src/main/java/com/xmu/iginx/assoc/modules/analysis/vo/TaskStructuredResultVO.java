package com.xmu.iginx.assoc.modules.analysis.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 结构化任务结果视图对象。
 */
@Data
public class TaskStructuredResultVO {

    /**
     * 当前任务 ID。
     */
    private String taskId;

    /**
     * 结果列名。
     */
    private List<String> columns;

    /**
     * 结果行数据。
     */
    private List<Map<String, Object>> rows;
}
