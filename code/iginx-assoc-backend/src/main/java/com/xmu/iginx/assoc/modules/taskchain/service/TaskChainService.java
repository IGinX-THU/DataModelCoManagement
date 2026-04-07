package com.xmu.iginx.assoc.modules.taskchain.service;

import com.xmu.iginx.assoc.modules.taskchain.dto.TaskChainRunRequest;
import com.xmu.iginx.assoc.modules.taskchain.dto.TaskChainSaveRequest;
import com.xmu.iginx.assoc.modules.taskchain.vo.TaskChainRuleOptionVO;
import com.xmu.iginx.assoc.modules.taskchain.vo.TaskChainRunVO;
import com.xmu.iginx.assoc.modules.taskchain.vo.TaskChainVO;

import java.util.List;

/**
 * 任务链服务。
 */
public interface TaskChainService {

    /**
     * 查询任务链列表。
     */
    List<TaskChainVO> listChains();

    /**
     * 查询任务链详情。
     */
    TaskChainVO getChain(Long chainId);

    /**
     * 创建任务链。
     */
    Long createChain(TaskChainSaveRequest request);

    /**
     * 更新任务链。
     */
    void updateChain(Long chainId, TaskChainSaveRequest request);

    /**
     * 删除任务链。
     */
    void deleteChain(Long chainId);

    /**
     * 查询可用于任务链的规则列表。
     */
    List<TaskChainRuleOptionVO> listCompatibleRules();

    /**
     * 提交任务链运行。
     */
    String submitRun(Long chainId, TaskChainRunRequest request);

    /**
     * 停止任务链运行。
     */
    void stopRun(String runId);

    /**
     * 查询运行记录列表。
     */
    List<TaskChainRunVO> listRuns(Long chainId);

    /**
     * 查询运行记录详情。
     */
    TaskChainRunVO getRun(String runId);
}
