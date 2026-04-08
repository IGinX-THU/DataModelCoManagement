package com.xmu.iginx.assoc.modules.task.service;

import com.xmu.iginx.assoc.modules.task.dto.TaskSubmitRequest;
import com.xmu.iginx.assoc.modules.task.vo.TaskVO;

import java.util.List;

/**
 * 任务服务接口。
 */
public interface TaskService {

    /**
     * 提交任务。
     *
     * @param request 提交参数
     * @return 任务 ID
     */
    String submitTask(TaskSubmitRequest request);

    /**
     * 终止任务。
     *
     * @param taskId 任务 ID
     */
    void stopTask(String taskId);

    /**
     * 删除任务记录。
     *
     * @param taskId 任务 ID
     */
    void deleteTask(String taskId);

    /**
     * 查询任务列表。
     *
     * @param ruleId 规则 ID（可选）
     * @return 任务列表
     */
    List<TaskVO> listTasks(Long ruleId);

    /**
     * 查询任务详情。
     *
     * @param taskId 任务 ID
     * @return 任务详情
     */
    TaskVO getTask(String taskId);
}
