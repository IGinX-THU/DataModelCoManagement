package com.xmu.iginx.assoc.modules.taskchain.model;

import lombok.Data;

import java.util.List;

/**
 * 任务链定义快照。
 */
@Data
public class TaskChainDefinition {

    private String chainName;
    private String chainMode;
    private List<TaskChainNodeDefinition> nodes;
}
