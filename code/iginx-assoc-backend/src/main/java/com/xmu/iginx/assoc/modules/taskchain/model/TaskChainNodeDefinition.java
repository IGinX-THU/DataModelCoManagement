package com.xmu.iginx.assoc.modules.taskchain.model;

import lombok.Data;

import java.util.Map;

/**
 * 任务链节点定义。
 */
@Data
public class TaskChainNodeDefinition {

    private String nodeId;
    private String nodeName;
    private Long ruleId;
    private Map<String, TaskChainInputSource> inputs;
}
