package com.xmu.iginx.assoc.modules.task.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务视图对象。
 */
@Data
public class TaskVO {

    private String id;
    private String taskName;
    private Long ruleId;
    private String status;
    private LocalDateTime rangeStart;
    private LocalDateTime rangeEnd;
    private LocalDateTime scheduledStartTime;
    private LocalDateTime scheduledEndTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String resultLink;
    /**
     * 本次任务每个输出参数的真实写回路径。
     * <p>
     * 当输出路径在规则中留空时，这里会返回解析后的默认路径，
     * 例如 task.result.&lt;taskId&gt;.&lt;outputName&gt;。
     * </p>
     */
    private Map<String, String> outputPaths;
    /**
     * 分析展示模式：TIME_SERIES / STRUCTURED。
     */
    private String analysisMode;
    private String execLog;
    private LocalDateTime createTime;
}
