package com.xmu.iginx.assoc.modules.external.controller;

import com.xmu.iginx.assoc.common.Result;
import com.xmu.iginx.assoc.modules.external.dto.ExternalAlgorithmJobRequest;
import com.xmu.iginx.assoc.modules.external.dto.ExternalDataExportJobRequest;
import com.xmu.iginx.assoc.modules.external.dto.ExternalDataImportJobRequest;
import com.xmu.iginx.assoc.modules.external.dto.ExternalModelJobRequest;
import com.xmu.iginx.assoc.modules.external.service.ExternalJobService;
import com.xmu.iginx.assoc.modules.external.vo.ExternalJobCreateResponse;
import com.xmu.iginx.assoc.modules.external.vo.ExternalJobResultResponse;
import com.xmu.iginx.assoc.modules.external.vo.ExternalJobStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 外部任务接口，提供模型/算法/数据任务的提交与查询。
 */
@Tag(name = "External")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/external")
public class ExternalJobController {

    private final ExternalJobService externalJobService;

    /**
     * 提交模型调用任务。
     *
     * @param request 提交请求
     * @param servletRequest 请求上下文
     * @return 任务创建结果
     */
    @Operation(summary = "提交模型调用任务")
    @PostMapping("/model-jobs")
    public Result<ExternalJobCreateResponse> submitModelJob(@Valid @RequestBody ExternalModelJobRequest request,
                                                            HttpServletRequest servletRequest) {
        return Result.success(externalJobService.submitModelJob(request, servletRequest.getRequestId()));
    }

    /**
     * 提交算法调用任务。
     *
     * @param request 提交请求
     * @param servletRequest 请求上下文
     * @return 任务创建结果
     */
    @Operation(summary = "提交算法调用任务")
    @PostMapping("/algorithm-jobs")
    public Result<ExternalJobCreateResponse> submitAlgorithmJob(@Valid @RequestBody ExternalAlgorithmJobRequest request,
                                                                HttpServletRequest servletRequest) {
        return Result.success(externalJobService.submitAlgorithmJob(request, servletRequest.getRequestId()));
    }

    /**
     * 提交数据导入任务。
     *
     * @param request 导入请求
     * @param file 导入文件
     * @param servletRequest 请求上下文
     * @return 任务创建结果
     */
    @Operation(summary = "提交数据导入任务")
    @PostMapping(value = "/data-import-jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ExternalJobCreateResponse> submitDataImportJob(
        @Valid @RequestPart("request") ExternalDataImportJobRequest request,
        @RequestPart("file") MultipartFile file,
        HttpServletRequest servletRequest
    ) {
        return Result.success(externalJobService.submitDataImportJob(request, file, servletRequest.getRequestId()));
    }

    /**
     * 提交数据导出任务。
     *
     * @param request 导出请求
     * @param servletRequest 请求上下文
     * @return 任务创建结果
     */
    @Operation(summary = "提交数据导出任务")
    @PostMapping("/data-export-jobs")
    public Result<ExternalJobCreateResponse> submitDataExportJob(@Valid @RequestBody ExternalDataExportJobRequest request,
                                                                 HttpServletRequest servletRequest) {
        return Result.success(externalJobService.submitDataExportJob(request, servletRequest.getRequestId()));
    }

    /**
     * 查询外部任务状态。
     *
     * @param jobId 任务 ID
     * @return 任务状态
     */
    @Operation(summary = "查询外部任务状态")
    @GetMapping("/jobs/{jobId}")
    public Result<ExternalJobStatusResponse> getJobStatus(@PathVariable String jobId) {
        return Result.success(externalJobService.getJobStatus(jobId));
    }

    /**
     * 查询外部任务结果。
     *
     * @param jobId 任务 ID
     * @return 任务结果
     */
    @Operation(summary = "查询外部任务结果")
    @GetMapping("/jobs/{jobId}/result")
    public Result<ExternalJobResultResponse> getJobResult(@PathVariable String jobId) {
        return Result.success(externalJobService.getJobResult(jobId));
    }
}
