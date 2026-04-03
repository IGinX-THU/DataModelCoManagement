package com.xmu.iginx.assoc.modules.analysis.vo;

import com.xmu.iginx.assoc.common.PageResult;
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
     * 分页结果。
     */
    private PageResult<Map<String, Object>> page;

    /**
     * 结构化图表使用的完整结果行。
     */
    private List<Map<String, Object>> chartRows;
}
