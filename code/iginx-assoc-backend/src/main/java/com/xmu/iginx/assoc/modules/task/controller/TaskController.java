package com.xmu.iginx.assoc.modules.task.controller;

import com.xmu.iginx.assoc.common.Result;
import com.xmu.iginx.assoc.modules.task.dto.TaskSubmitRequest;
import com.xmu.iginx.assoc.modules.task.service.TaskService;
import com.xmu.iginx.assoc.modules.task.vo.TaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务管理接口，提供任务提交、终止与查询能力。
 */
@Tag(name = "Task Management")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    /**
     * 提交计算任务。
     *
     * @param request 提交参数
     * @return 任务 ID
     */
    @Operation(summary = "提交计算任务")
    @PostMapping("/submit")
    public Result<String> submit(@Valid @RequestBody TaskSubmitRequest request) {
        return Result.success(taskService.submitTask(request));
    }

    /**
     * 终止任务执行。
     *
     * @param id 任务 ID
     * @return 操作结果
     */
    @Operation(summary = "终止任务")
    @PostMapping("/{id}/stop")
    public Result<Void> stop(@PathVariable String id) {
        taskService.stopTask(id);
        return Result.success();
    }

    /**
     * 查询任务列表。
     *
     * @param ruleId 规则 ID（可选）
     * @return 任务列表
     */
    @Operation(summary = "任务列表")
    @GetMapping
    public Result<List<TaskVO>> list(@RequestParam(required = false) Long ruleId) {
        return Result.success(taskService.listTasks(ruleId));
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    @Operation(summary = "任务详情")
    @GetMapping("/{id}")
    public Result<TaskVO> detail(@PathVariable String id) {
        return Result.success(taskService.getTask(id));
    }
}
