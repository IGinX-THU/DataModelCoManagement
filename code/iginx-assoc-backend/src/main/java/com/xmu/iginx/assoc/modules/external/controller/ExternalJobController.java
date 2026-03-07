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

@Tag(name = "External")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/external")
public class ExternalJobController {

    private final ExternalJobService externalJobService;

    @Operation(summary = "Submit model invocation job")
    @PostMapping("/model-jobs")
    public Result<ExternalJobCreateResponse> submitModelJob(@Valid @RequestBody ExternalModelJobRequest request,
                                                            HttpServletRequest servletRequest) {
        return Result.success(externalJobService.submitModelJob(request, servletRequest.getRequestId()));
    }

    @Operation(summary = "Submit algorithm invocation job")
    @PostMapping("/algorithm-jobs")
    public Result<ExternalJobCreateResponse> submitAlgorithmJob(@Valid @RequestBody ExternalAlgorithmJobRequest request,
                                                                HttpServletRequest servletRequest) {
        return Result.success(externalJobService.submitAlgorithmJob(request, servletRequest.getRequestId()));
    }

    @Operation(summary = "Submit data import job")
    @PostMapping(value = "/data-import-jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ExternalJobCreateResponse> submitDataImportJob(
        @Valid @RequestPart("request") ExternalDataImportJobRequest request,
        @RequestPart("file") MultipartFile file,
        HttpServletRequest servletRequest
    ) {
        return Result.success(externalJobService.submitDataImportJob(request, file, servletRequest.getRequestId()));
    }

    @Operation(summary = "Submit data export job")
    @PostMapping("/data-export-jobs")
    public Result<ExternalJobCreateResponse> submitDataExportJob(@Valid @RequestBody ExternalDataExportJobRequest request,
                                                                 HttpServletRequest servletRequest) {
        return Result.success(externalJobService.submitDataExportJob(request, servletRequest.getRequestId()));
    }

    @Operation(summary = "Get external job status")
    @GetMapping("/jobs/{jobId}")
    public Result<ExternalJobStatusResponse> getJobStatus(@PathVariable String jobId) {
        return Result.success(externalJobService.getJobStatus(jobId));
    }

    @Operation(summary = "Get external job result")
    @GetMapping("/jobs/{jobId}/result")
    public Result<ExternalJobResultResponse> getJobResult(@PathVariable String jobId) {
        return Result.success(externalJobService.getJobResult(jobId));
    }
}
