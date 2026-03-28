package com.xmu.iginx.assoc.modules.analysis.controller;

import com.xmu.iginx.assoc.common.Result;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskCompareRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskExportRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskReportRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskSeriesRequest;
import com.xmu.iginx.assoc.modules.analysis.service.AnalysisService;
import com.xmu.iginx.assoc.modules.analysis.vo.TaskAnalysisResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * 分析模块接口，提供任务曲线、对比、导出与报告生成能力。
 */
@Tag(name = "Analysis")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    /**
     * 获取单个任务的时序曲线数据。
     *
     * @param taskId 任务 ID
     * @param request 曲线请求参数
     * @return 分析结果
     */
    @Operation(summary = "获取任务曲线")
    @GetMapping("/tasks/{taskId}/series")
    public Result<TaskAnalysisResultVO> series(@PathVariable String taskId,
                                               TaskSeriesRequest request) {
        return Result.success(analysisService.queryTaskSeries(taskId, request));
    }

    /**
     * 对多个任务进行曲线对比。
     *
     * @param request 对比请求
     * @return 分析结果
     */
    @Operation(summary = "任务对比曲线")
    @PostMapping("/tasks/compare")
    public Result<TaskAnalysisResultVO> compare(@Valid @RequestBody TaskCompareRequest request) {
        return Result.success(analysisService.compareTasks(request));
    }

    /**
     * 导出任务资源包（含元数据/输入输出/模型文件等）。
     *
     * @param taskId 任务 ID
     * @param request 导出参数
     * @return 下载路径
     */
    @Operation(summary = "导出资源包")
    @PostMapping("/tasks/{taskId}/export")
    public Result<String> export(@PathVariable String taskId,
                                 @RequestBody TaskExportRequest request) {
        return Result.success(analysisService.exportPackage(taskId, request));
    }

    /**
     * 生成任务实验报告并返回下载路径。
     *
     * @param taskId 任务 ID
     * @param request 报告生成参数
     * @return 下载路径
     */
    @Operation(summary = "生成实验报告")
    @PostMapping("/tasks/{taskId}/report")
    public Result<String> report(@PathVariable String taskId,
                                 @RequestBody TaskReportRequest request) {
        return Result.success(analysisService.generateReport(taskId, request));
    }
}
