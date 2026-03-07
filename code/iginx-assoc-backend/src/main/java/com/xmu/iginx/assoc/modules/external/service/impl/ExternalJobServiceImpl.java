package com.xmu.iginx.assoc.modules.external.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskCompareRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskExportRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskReportRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskSeriesRequest;
import com.xmu.iginx.assoc.modules.analysis.service.AnalysisService;
import com.xmu.iginx.assoc.modules.analysis.vo.TaskSeriesVO;
import com.xmu.iginx.assoc.modules.data.dto.DataExportRequest;
import com.xmu.iginx.assoc.modules.data.service.DataExportService;
import com.xmu.iginx.assoc.modules.data.service.DataImportService;
import com.xmu.iginx.assoc.modules.data.util.DataFileStorageService;
import com.xmu.iginx.assoc.modules.data.vo.DataExportResultVO;
import com.xmu.iginx.assoc.modules.data.vo.DataImportResultVO;
import com.xmu.iginx.assoc.modules.external.dto.ExternalAlgorithmJobRequest;
import com.xmu.iginx.assoc.modules.external.dto.ExternalDataExportJobRequest;
import com.xmu.iginx.assoc.modules.external.dto.ExternalDataImportJobRequest;
import com.xmu.iginx.assoc.modules.external.dto.ExternalJobCreateRequest;
import com.xmu.iginx.assoc.modules.external.dto.ExternalModelJobRequest;
import com.xmu.iginx.assoc.modules.external.entity.ExternalJobEntity;
import com.xmu.iginx.assoc.modules.external.enums.ExternalJobStatus;
import com.xmu.iginx.assoc.modules.external.enums.ExternalJobType;
import com.xmu.iginx.assoc.modules.external.repository.ExternalJobRepository;
import com.xmu.iginx.assoc.modules.external.service.ExternalJobService;
import com.xmu.iginx.assoc.modules.external.util.PathMultipartFile;
import com.xmu.iginx.assoc.modules.external.vo.ExternalErrorResponse;
import com.xmu.iginx.assoc.modules.external.vo.ExternalJobCreateResponse;
import com.xmu.iginx.assoc.modules.external.vo.ExternalJobResultResponse;
import com.xmu.iginx.assoc.modules.external.vo.ExternalJobStatusResponse;
import com.xmu.iginx.assoc.modules.task.dto.TaskSubmitRequest;
import com.xmu.iginx.assoc.modules.task.service.TaskScheduler;
import com.xmu.iginx.assoc.modules.task.service.TaskService;
import com.xmu.iginx.assoc.modules.task.vo.TaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalJobServiceImpl implements ExternalJobService {

    private static final String ERROR_CODE_INVALID_ARGUMENT = "INVALID_ARGUMENT";
    private static final String ERROR_CODE_RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    private static final String ERROR_CODE_EXECUTION_FAILED = "EXECUTION_FAILED";
    private static final String ERROR_CODE_TIMEOUT = "TIMEOUT";
    private static final String ERROR_CODE_PERMISSION_DENIED = "PERMISSION_DENIED";
    private static final String ERROR_CODE_CANCELED = "CANCELED";
    private static final String ERROR_CODE_INTERNAL = "INTERNAL_ERROR";

    private final ExternalJobRepository externalJobRepository;
    private final TaskScheduler taskScheduler;
    private final TaskService taskService;
    private final AnalysisService analysisService;
    private final DataImportService dataImportService;
    private final DataExportService dataExportService;
    private final DataFileStorageService dataFileStorageService;
    private final ObjectMapper objectMapper;

    @Override
    public ExternalJobCreateResponse submitModelJob(ExternalModelJobRequest request, String traceId) {
        ExternalJobCreateRequest payload = new ExternalJobCreateRequest();
        payload.setJobType(ExternalJobType.MODEL_CALL);
        payload.setModelJob(request);
        return submitAndSchedule(payload, traceId);
    }

    @Override
    public ExternalJobCreateResponse submitAlgorithmJob(ExternalAlgorithmJobRequest request, String traceId) {
        ExternalJobCreateRequest payload = new ExternalJobCreateRequest();
        payload.setJobType(ExternalJobType.ALGORITHM_CALL);
        payload.setAlgorithmJob(request);
        return submitAndSchedule(payload, traceId);
    }

    @Override
    public ExternalJobCreateResponse submitDataImportJob(ExternalDataImportJobRequest request,
                                                         MultipartFile file,
                                                         String traceId) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("导入文件不能为空");
        }
        ExternalJobCreateRequest payload = new ExternalJobCreateRequest();
        payload.setJobType(ExternalJobType.DATA_IMPORT);
        payload.setDataImportJob(request);
        Path stagedPath = stageImportFile(file);
        payload.setStagedFilePath(stagedPath.toAbsolutePath().toString());
        payload.setStagedFileName(resolveStagedFileName(file));
        payload.setStagedContentType(file.getContentType());
        return submitAndSchedule(payload, traceId);
    }

    @Override
    public ExternalJobCreateResponse submitDataExportJob(ExternalDataExportJobRequest request, String traceId) {
        ExternalJobCreateRequest payload = new ExternalJobCreateRequest();
        payload.setJobType(ExternalJobType.DATA_EXPORT);
        payload.setDataExportJob(request);
        return submitAndSchedule(payload, traceId);
    }

    @Override
    public ExternalJobStatusResponse getJobStatus(String jobId) {
        ExternalJobEntity entity = findJob(jobId);
        ExternalJobStatusResponse response = new ExternalJobStatusResponse();
        response.setJobId(entity.getId());
        response.setJobType(entity.getJobType());
        response.setStatus(entity.getStatus());
        response.setSubmitTime(entity.getSubmitTime());
        response.setStartTime(entity.getStartTime());
        response.setFinishTime(entity.getFinishTime());
        response.setError(buildError(entity));
        return response;
    }

    @Override
    public ExternalJobResultResponse getJobResult(String jobId) {
        ExternalJobEntity entity = findJob(jobId);
        ExternalJobResultResponse response = new ExternalJobResultResponse();
        response.setJobId(entity.getId());
        response.setStatus(entity.getStatus());
        response.setFinishTime(entity.getFinishTime());
        response.setError(buildError(entity));
        Object result = parseJson(entity.getResultJson());
        response.setResult(result);
        response.setDownloadUrl(extractDownloadUrl(result));
        return response;
    }

    private ExternalJobCreateResponse submitAndSchedule(ExternalJobCreateRequest payload, String traceId) {
        ExternalJobEntity job = buildPendingJob(payload, traceId);
        externalJobRepository.save(job);
        try {
            taskScheduler.submit(job.getId(), () -> executeJob(job.getId(), payload));
        } catch (BizException ex) {
            markFailed(job.getId(), ERROR_CODE_EXECUTION_FAILED, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            markFailed(job.getId(), ERROR_CODE_EXECUTION_FAILED, ex.getMessage());
            throw BizException.internal("外部任务提交失败: " + ex.getMessage());
        }
        return toCreateResponse(job);
    }

    private ExternalJobEntity buildPendingJob(ExternalJobCreateRequest payload, String traceId) {
        ExternalJobEntity job = new ExternalJobEntity();
        job.setId(UUID.randomUUID().toString().replace("-", ""));
        job.setJobType(payload.getJobType().name());
        job.setStatus(ExternalJobStatus.PENDING.name());
        job.setSubmitTime(LocalDateTime.now());
        job.setTraceId(traceId);
        try {
            job.setRequestJson(objectMapper.writeValueAsString(payload));
        } catch (Exception ignored) {
            job.setRequestJson("{}");
        }
        return job;
    }

    private void executeJob(String jobId, ExternalJobCreateRequest payload) {
        ExternalJobEntity job = findJob(jobId);
        job.setStatus(ExternalJobStatus.RUNNING.name());
        job.setStartTime(LocalDateTime.now());
        externalJobRepository.save(job);
        try {
            Object result = switch (payload.getJobType()) {
                case MODEL_CALL -> executeModelJob(payload.getModelJob());
                case ALGORITHM_CALL -> executeAlgorithmJob(payload.getAlgorithmJob());
                case DATA_IMPORT -> executeDataImportJob(payload);
                case DATA_EXPORT -> executeDataExportJob(payload.getDataExportJob());
            };
            job.setStatus(ExternalJobStatus.SUCCEEDED.name());
            job.setResultJson(writeJson(result));
            job.setErrorCode(null);
            job.setErrorMessage(null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            job.setStatus(ExternalJobStatus.CANCELED.name());
            job.setErrorCode(ERROR_CODE_CANCELED);
            job.setErrorMessage("任务被取消: " + ex.getMessage());
        } catch (BizException ex) {
            job.setStatus(ExternalJobStatus.FAILED.name());
            job.setErrorCode(resolveBizErrorCode(ex));
            job.setErrorMessage(ex.getMessage());
        } catch (Exception ex) {
            job.setStatus(ExternalJobStatus.FAILED.name());
            job.setErrorCode(ERROR_CODE_INTERNAL);
            job.setErrorMessage(ex.getMessage());
        } finally {
            job.setFinishTime(LocalDateTime.now());
            externalJobRepository.save(job);
            taskScheduler.clear(jobId);
            cleanupStagedFile(payload.getStagedFilePath());
        }
    }

    private Object executeModelJob(ExternalModelJobRequest request) throws InterruptedException {
        if (request == null) {
            throw BizException.badRequest("模型任务请求不能为空");
        }
        TaskSubmitRequest taskSubmitRequest = new TaskSubmitRequest();
        taskSubmitRequest.setRuleId(request.getRuleId());
        taskSubmitRequest.setTimeRange(request.getTimeRange());
        String innerTaskId = taskService.submitTask(taskSubmitRequest);

        int pollInterval = request.getPollIntervalSeconds() == null ? 1 : request.getPollIntervalSeconds();
        int timeoutSeconds = request.getTimeoutSeconds() == null ? 1800 : request.getTimeoutSeconds();
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() <= deadline) {
            TaskVO taskVO = taskService.getTask(innerTaskId);
            String status = taskVO.getStatus() == null ? "" : taskVO.getStatus().toUpperCase(Locale.ROOT);
            if ("SUCCESS".equals(status)) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("innerTaskId", taskVO.getId());
                result.put("innerTaskStatus", taskVO.getStatus());
                result.put("resultLink", taskVO.getResultLink());
                result.put("execLog", taskVO.getExecLog());
                result.put("startTime", taskVO.getStartTime());
                result.put("endTime", taskVO.getEndTime());
                return result;
            }
            if ("FAILED".equals(status) || "ABORTED".equals(status)) {
                throw BizException.internal("模型任务执行失败: " + taskVO.getExecLog());
            }
            TimeUnit.SECONDS.sleep(pollInterval);
        }
        throw BizException.internal("模型任务执行超时");
    }

    private Object executeAlgorithmJob(ExternalAlgorithmJobRequest request) {
        if (request == null) {
            throw BizException.badRequest("算法任务请求不能为空");
        }
        String action = request.getAction() == null ? "" : request.getAction().trim().toUpperCase(Locale.ROOT);
        return switch (action) {
            case "TASK_SERIES", "SERIES" -> executeTaskSeries(request);
            case "TASK_COMPARE", "COMPARE" -> executeTaskCompare(request);
            case "TASK_EXPORT", "EXPORT" -> executeTaskExport(request);
            case "TASK_REPORT", "REPORT" -> executeTaskReport(request);
            default -> throw BizException.badRequest("不支持的算法动作: " + request.getAction());
        };
    }

    private Object executeTaskSeries(ExternalAlgorithmJobRequest request) {
        if (!StringUtils.hasText(request.getTaskId())) {
            throw BizException.badRequest("taskId 不能为空");
        }
        TaskSeriesRequest seriesRequest = new TaskSeriesRequest();
        seriesRequest.setRelative(Boolean.TRUE.equals(request.getRelative()));
        List<TaskSeriesVO> series = analysisService.queryTaskSeries(request.getTaskId(), seriesRequest);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", "TASK_SERIES");
        result.put("taskId", request.getTaskId());
        result.put("series", series);
        return result;
    }

    private Object executeTaskCompare(ExternalAlgorithmJobRequest request) {
        if (request.getTaskIds() == null || request.getTaskIds().isEmpty()) {
            throw BizException.badRequest("taskIds 不能为空");
        }
        TaskCompareRequest compareRequest = new TaskCompareRequest();
        compareRequest.setTaskIds(request.getTaskIds());
        if (StringUtils.hasText(request.getMode())) {
            compareRequest.setMode(request.getMode());
        }
        List<TaskSeriesVO> series = analysisService.compareTasks(compareRequest);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", "TASK_COMPARE");
        result.put("taskIds", request.getTaskIds());
        result.put("series", series);
        return result;
    }

    private Object executeTaskExport(ExternalAlgorithmJobRequest request) {
        if (!StringUtils.hasText(request.getTaskId())) {
            throw BizException.badRequest("taskId 不能为空");
        }
        TaskExportRequest exportRequest = new TaskExportRequest();
        if (request.getIncludeModel() != null) {
            exportRequest.setIncludeModel(request.getIncludeModel());
        }
        if (request.getIncludeInput() != null) {
            exportRequest.setIncludeInput(request.getIncludeInput());
        }
        if (request.getIncludeOutput() != null) {
            exportRequest.setIncludeOutput(request.getIncludeOutput());
        }
        if (StringUtils.hasText(request.getFormat())) {
            exportRequest.setFormat(request.getFormat());
        }
        String packagePath = analysisService.exportPackage(request.getTaskId(), exportRequest);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", "TASK_EXPORT");
        result.put("taskId", request.getTaskId());
        result.put("packagePath", packagePath);
        return result;
    }

    private Object executeTaskReport(ExternalAlgorithmJobRequest request) {
        if (!StringUtils.hasText(request.getTaskId())) {
            throw BizException.badRequest("taskId 不能为空");
        }
        TaskReportRequest reportRequest = new TaskReportRequest();
        if (request.getIncludeStats() != null) {
            reportRequest.setIncludeStats(request.getIncludeStats());
        }
        if (request.getIncludeCharts() != null) {
            reportRequest.setIncludeCharts(request.getIncludeCharts());
        }
        String reportPath = analysisService.generateReport(request.getTaskId(), reportRequest);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", "TASK_REPORT");
        result.put("taskId", request.getTaskId());
        result.put("reportPath", reportPath);
        return result;
    }

    private Object executeDataImportJob(ExternalJobCreateRequest payload) {
        ExternalDataImportJobRequest request = payload.getDataImportJob();
        if (request == null) {
            throw BizException.badRequest("导入任务请求不能为空");
        }
        if (!StringUtils.hasText(payload.getStagedFilePath())) {
            throw BizException.badRequest("导入文件不存在");
        }
        Path filePath = Path.of(payload.getStagedFilePath());
        MultipartFile multipartFile = new PathMultipartFile(filePath, payload.getStagedFileName(), payload.getStagedContentType());
        String type = request.getImportType() == null ? "" : request.getImportType().trim().toUpperCase(Locale.ROOT);
        DataImportResultVO result = switch (type) {
            case "TS", "TIME_SERIES", "TIMESERIES" -> {
                if (request.getTimeSeriesRequest() == null) {
                    throw BizException.badRequest("timeSeriesRequest 不能为空");
                }
                yield dataImportService.importTimeSeries(request.getTimeSeriesRequest(), multipartFile);
            }
            case "STRUCT", "STRUCTURED" -> {
                if (request.getStructuredRequest() == null) {
                    throw BizException.badRequest("structuredRequest 不能为空");
                }
                yield dataImportService.importStructured(request.getStructuredRequest(), multipartFile);
            }
            default -> throw BizException.badRequest("不支持的导入类型: " + request.getImportType());
        };
        Map<String, Object> wrappedResult = new LinkedHashMap<>();
        wrappedResult.put("importType", type);
        wrappedResult.put("result", result);
        return wrappedResult;
    }

    private Object executeDataExportJob(ExternalDataExportJobRequest request) {
        if (request == null || request.getExportRequest() == null) {
            throw BizException.badRequest("导出请求不能为空");
        }
        DataExportRequest exportRequest;
        try {
            exportRequest = objectMapper.convertValue(request.getExportRequest(), DataExportRequest.class);
        } catch (Exception ex) {
            throw BizException.badRequest("导出参数格式错误");
        }
        exportRequest.setAsync(false);
        DataExportResultVO result = dataExportService.exportData(exportRequest);
        Map<String, Object> wrappedResult = new LinkedHashMap<>();
        wrappedResult.put("exportType", exportRequest.getType());
        wrappedResult.put("result", result);
        return wrappedResult;
    }

    private ExternalJobEntity findJob(String jobId) {
        return externalJobRepository.findById(jobId)
            .orElseThrow(() -> BizException.badRequest("外部任务不存在: " + jobId));
    }

    private void markFailed(String jobId, String code, String message) {
        try {
            ExternalJobEntity entity = findJob(jobId);
            entity.setStatus(ExternalJobStatus.FAILED.name());
            entity.setErrorCode(code);
            entity.setErrorMessage(message);
            entity.setFinishTime(LocalDateTime.now());
            externalJobRepository.save(entity);
        } catch (Exception ignored) {
        }
    }

    private ExternalJobCreateResponse toCreateResponse(ExternalJobEntity entity) {
        ExternalJobCreateResponse response = new ExternalJobCreateResponse();
        response.setJobId(entity.getId());
        response.setStatus(entity.getStatus());
        response.setSubmitTime(entity.getSubmitTime());
        return response;
    }

    private String writeJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            return "{\"raw\":\"序列化失败\"}";
        }
    }

    private Object parseJson(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return objectMapper.readValue(text, Object.class);
        } catch (Exception ex) {
            return text;
        }
    }

    private ExternalErrorResponse buildError(ExternalJobEntity entity) {
        if (!StringUtils.hasText(entity.getErrorCode()) && !StringUtils.hasText(entity.getErrorMessage())) {
            return null;
        }
        ExternalErrorResponse error = new ExternalErrorResponse();
        error.setCode(entity.getErrorCode());
        error.setMessage(entity.getErrorMessage());
        error.setTraceId(entity.getTraceId());
        return error;
    }

    private String extractDownloadUrl(Object result) {
        if (!(result instanceof Map<?, ?> resultMap)) {
            return null;
        }
        Object innerResult = resultMap.get("result");
        if (innerResult instanceof Map<?, ?> inner) {
            Object value = inner.get("downloadUrl");
            if (value != null) {
                return String.valueOf(value);
            }
        }
        Object direct = resultMap.get("downloadUrl");
        if (direct != null) {
            return String.valueOf(direct);
        }
        return null;
    }

    private Path stageImportFile(MultipartFile file) {
        String extension = extractExtension(file.getOriginalFilename());
        DataFileStorageService.StoredFile storedFile = dataFileStorageService.createFile("external_import",
            extension.isBlank() ? ".dat" : "." + extension);
        try {
            file.transferTo(storedFile.path());
            return storedFile.path();
        } catch (Exception ex) {
            throw BizException.internal("导入文件缓存失败: " + ex.getMessage());
        }
    }

    private String resolveStagedFileName(MultipartFile file) {
        if (!StringUtils.hasText(file.getOriginalFilename())) {
            return "external_import.dat";
        }
        return file.getOriginalFilename();
    }

    private String extractExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).trim().toLowerCase(Locale.ROOT);
    }

    private void cleanupStagedFile(String stagedFilePath) {
        if (!StringUtils.hasText(stagedFilePath)) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(stagedFilePath));
        } catch (Exception ex) {
            log.debug("清理临时文件失败: {}", stagedFilePath);
        }
    }

    private String resolveBizErrorCode(BizException ex) {
        if (ex == null || ex.getCode() == null) {
            return ERROR_CODE_EXECUTION_FAILED;
        }
        return switch (ex.getCode()) {
            case 400 -> ERROR_CODE_INVALID_ARGUMENT;
            case 401, 403 -> ERROR_CODE_PERMISSION_DENIED;
            case 404 -> ERROR_CODE_RESOURCE_NOT_FOUND;
            case 408 -> ERROR_CODE_TIMEOUT;
            default -> ERROR_CODE_EXECUTION_FAILED;
        };
    }
}
