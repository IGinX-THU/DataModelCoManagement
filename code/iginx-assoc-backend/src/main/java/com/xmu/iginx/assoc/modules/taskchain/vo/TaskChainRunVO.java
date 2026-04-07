package com.xmu.iginx.assoc.modules.taskchain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务链运行视图对象。
 */
@Data
public class TaskChainRunVO {

    private String id;
    private Long chainId;
    private String chainName;
    private String runName;
    private String status;
    private String chainMode;
    private LocalDateTime rangeStart;
    private LocalDateTime rangeEnd;
    private LocalDateTime scheduledStartTime;
    private LocalDateTime scheduledEndTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String resultPrefix;
    private String execLog;
    private List<NodeRunVO> nodes;
    private LocalDateTime createTime;

    /**
     * 节点运行视图。
     */
    @Data
    public static class NodeRunVO {

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
}
