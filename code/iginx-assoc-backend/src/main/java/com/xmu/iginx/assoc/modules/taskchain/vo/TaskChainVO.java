package com.xmu.iginx.assoc.modules.taskchain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务链视图对象。
 */
@Data
public class TaskChainVO {

    private Long id;
    private String chainName;
    private String chainMode;
    private Integer nodeCount;
    private Integer edgeCount;
    private List<NodeVO> nodes;
    private LocalDateTime updateTime;

    /**
     * 节点视图。
     */
    @Data
    public static class NodeVO {

        private String nodeId;
        private String nodeName;
        private Long ruleId;
        private String ruleName;
        private String functionName;
        private String modelName;
        private String modelVersion;
        private String modelType;
        private List<ParamVO> availableInputs;
        private List<ParamVO> availableOutputs;
        private Map<String, InputSourceVO> inputSources;
        private String validationMessage;
    }

    /**
     * 参数视图。
     */
    @Data
    public static class ParamVO {

        private String name;
        private String type;
        private String defaultPath;
    }

    /**
     * 输入来源视图。
     */
    @Data
    public static class InputSourceVO {

        private String sourceType;
        private String path;
        private String sourceNodeId;
        private String sourceOutputName;
    }
}
