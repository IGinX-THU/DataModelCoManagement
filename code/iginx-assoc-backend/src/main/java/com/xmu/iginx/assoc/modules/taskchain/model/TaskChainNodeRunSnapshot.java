package com.xmu.iginx.assoc.modules.taskchain.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务链节点运行快照。
 */
@Data
public class TaskChainNodeRunSnapshot {

    private String nodeId;
    private String nodeName;
    private Long ruleId;
    private String ruleName;
    private String functionName;
    private String modelName;
    private String modelVersion;
    private String modelType;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String execLog;
    private Map<String, String> outputPaths;
    private Map<String, Integer> outputValueCounts;
}
