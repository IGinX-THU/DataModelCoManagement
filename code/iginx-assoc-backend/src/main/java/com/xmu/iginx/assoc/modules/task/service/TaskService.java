package com.xmu.iginx.assoc.modules.task.service;

import com.xmu.iginx.assoc.modules.task.dto.TaskSubmitRequest;
import com.xmu.iginx.assoc.modules.task.vo.TaskVO;

import java.util.List;

public interface TaskService {

    String submitTask(TaskSubmitRequest request);

    void stopTask(String taskId);

    List<TaskVO> listTasks(Long ruleId);

    TaskVO getTask(String taskId);
}
