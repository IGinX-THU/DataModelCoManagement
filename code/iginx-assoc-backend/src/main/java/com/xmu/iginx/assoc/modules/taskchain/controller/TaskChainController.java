package com.xmu.iginx.assoc.modules.taskchain.controller;

import com.xmu.iginx.assoc.common.Result;
import com.xmu.iginx.assoc.modules.taskchain.dto.TaskChainRunRequest;
import com.xmu.iginx.assoc.modules.taskchain.dto.TaskChainSaveRequest;
import com.xmu.iginx.assoc.modules.taskchain.service.TaskChainService;
import com.xmu.iginx.assoc.modules.taskchain.vo.TaskChainRuleOptionVO;
import com.xmu.iginx.assoc.modules.taskchain.vo.TaskChainRunVO;
import com.xmu.iginx.assoc.modules.taskchain.vo.TaskChainVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务链管理接口。
 */
@Tag(name = "Task Chain Management")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/task-chains")
public class TaskChainController {

    private final TaskChainService taskChainService;

    /**
     * 查询任务链列表。
     */
    @Operation(summary = "任务链列表")
    @GetMapping
    public Result<List<TaskChainVO>> list() {
        return Result.success(taskChainService.listChains());
    }

    /**
     * 查询任务链详情。
     */
    @Operation(summary = "任务链详情")
    @GetMapping("/{id}")
    public Result<TaskChainVO> detail(@PathVariable Long id) {
        return Result.success(taskChainService.getChain(id));
    }

    /**
     * 创建任务链。
     */
    @Operation(summary = "创建任务链")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody TaskChainSaveRequest request) {
        return Result.success(taskChainService.createChain(request));
    }

    /**
     * 更新任务链。
     */
    @Operation(summary = "更新任务链")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TaskChainSaveRequest request) {
        taskChainService.updateChain(id, request);
        return Result.success();
    }

    /**
     * 删除任务链。
     */
    @Operation(summary = "删除任务链")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskChainService.deleteChain(id);
        return Result.success();
    }

    /**
     * 查询可用于任务链的规则列表。
     */
    @Operation(summary = "任务链可选规则列表")
    @GetMapping("/compatible-rules")
    public Result<List<TaskChainRuleOptionVO>> compatibleRules() {
        return Result.success(taskChainService.listCompatibleRules());
    }

    /**
     * 提交任务链运行。
     */
    @Operation(summary = "提交任务链运行")
    @PostMapping("/{id}/runs")
    public Result<String> submitRun(@PathVariable Long id, @Valid @RequestBody TaskChainRunRequest request) {
        return Result.success(taskChainService.submitRun(id, request));
    }

    /**
     * 查询任务链运行列表。
     */
    @Operation(summary = "任务链运行列表")
    @GetMapping("/runs")
    public Result<List<TaskChainRunVO>> listRuns(@RequestParam(required = false) Long chainId) {
        return Result.success(taskChainService.listRuns(chainId));
    }

    /**
     * 查询任务链运行详情。
     */
    @Operation(summary = "任务链运行详情")
    @GetMapping("/runs/{runId}")
    public Result<TaskChainRunVO> runDetail(@PathVariable String runId) {
        return Result.success(taskChainService.getRun(runId));
    }

    /**
     * 停止任务链运行。
     */
    @Operation(summary = "停止任务链运行")
    @PostMapping("/runs/{runId}/stop")
    public Result<Void> stopRun(@PathVariable String runId) {
        taskChainService.stopRun(runId);
        return Result.success();
    }

    /**
     * 删除任务链运行记录。
     */
    @Operation(summary = "删除任务链运行记录")
    @DeleteMapping("/runs/{runId}")
    public Result<Void> deleteRun(@PathVariable String runId) {
        taskChainService.deleteRun(runId);
        return Result.success();
    }
}
