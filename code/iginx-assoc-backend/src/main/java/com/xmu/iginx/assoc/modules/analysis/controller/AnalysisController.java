package com.xmu.iginx.assoc.modules.analysis.controller;

import com.xmu.iginx.assoc.common.Result;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskCompareRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskExportRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskReportRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskSeriesRequest;
import com.xmu.iginx.assoc.modules.analysis.service.AnalysisService;
import com.xmu.iginx.assoc.modules.analysis.vo.TaskSeriesVO;
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

import java.util.List;

@Tag(name = "Analysis")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    @Operation(summary = "获取任务曲线")
    @GetMapping("/tasks/{taskId}/series")
    public Result<List<TaskSeriesVO>> series(@PathVariable String taskId,
                                             TaskSeriesRequest request) {
        return Result.success(analysisService.queryTaskSeries(taskId, request));
    }

    @Operation(summary = "任务对比曲线")
    @PostMapping("/tasks/compare")
    public Result<List<TaskSeriesVO>> compare(@Valid @RequestBody TaskCompareRequest request) {
        return Result.success(analysisService.compareTasks(request));
    }

    @Operation(summary = "导出资源包")
    @PostMapping("/tasks/{taskId}/export")
    public Result<String> export(@PathVariable String taskId,
                                 @RequestBody TaskExportRequest request) {
        return Result.success(analysisService.exportPackage(taskId, request));
    }

    @Operation(summary = "生成实验报告")
    @PostMapping("/tasks/{taskId}/report")
    public Result<String> report(@PathVariable String taskId,
                                 @RequestBody TaskReportRequest request) {
        return Result.success(analysisService.generateReport(taskId, request));
    }
}
