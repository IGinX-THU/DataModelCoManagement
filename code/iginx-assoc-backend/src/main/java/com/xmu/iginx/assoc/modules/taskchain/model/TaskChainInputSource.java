package com.xmu.iginx.assoc.modules.taskchain.model;

import lombok.Data;

/**
 * 任务链节点输入来源。
 */
@Data
public class TaskChainInputSource {

    private String sourceType;
    private String path;
    private String sourceNodeId;
    private String sourceOutputName;
}
