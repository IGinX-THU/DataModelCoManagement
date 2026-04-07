package com.xmu.iginx.assoc.modules.taskchain.model;

import lombok.Data;

import java.util.List;

/**
 * 任务链运行快照。
 */
@Data
public class TaskChainRunSnapshot {

    private Long chainId;
    private String chainName;
    private String chainMode;
    private String resultPrefix;
    private List<TaskChainNodeRunSnapshot> nodes;
}
