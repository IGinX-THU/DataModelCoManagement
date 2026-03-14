package com.xmu.iginx.assoc.modules.external.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.modules.data.dto.DataExportRequest;
import com.xmu.iginx.assoc.modules.data.service.DataExportService;
import com.xmu.iginx.assoc.modules.data.service.DataImportService;
import com.xmu.iginx.assoc.modules.data.util.DataFileStorageService;
import com.xmu.iginx.assoc.modules.data.vo.DataExportResultVO;
import com.xmu.iginx.assoc.modules.external.dto.ExternalAlgorithmJobRequest;
import com.xmu.iginx.assoc.modules.external.dto.ExternalDataExportJobRequest;
import com.xmu.iginx.assoc.modules.external.dto.ExternalModelJobRequest;
import com.xmu.iginx.assoc.modules.external.entity.ExternalJobEntity;
import com.xmu.iginx.assoc.modules.external.repository.ExternalJobRepository;
import com.xmu.iginx.assoc.modules.external.vo.ExternalJobResultResponse;
import com.xmu.iginx.assoc.modules.external.vo.ExternalJobStatusResponse;
import com.xmu.iginx.assoc.modules.task.dto.TaskSubmitRequest;
import com.xmu.iginx.assoc.modules.task.service.TaskScheduler;
import com.xmu.iginx.assoc.modules.task.service.TaskService;
import com.xmu.iginx.assoc.modules.task.vo.TaskVO;
import com.xmu.iginx.assoc.modules.analysis.service.AnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 外部任务服务测试。
 */
@ExtendWith(MockitoExtension.class)
class ExternalJobServiceImplTest {

    @Mock
    private ExternalJobRepository externalJobRepository;
    @Mock
    private TaskScheduler taskScheduler;
    @Mock
    private TaskService taskService;
    @Mock
    private AnalysisService analysisService;
    @Mock
    private DataImportService dataImportService;
    @Mock
    private DataExportService dataExportService;
    @Mock
    private DataFileStorageService dataFileStorageService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ExternalJobServiceImpl externalJobService;

    private final Map<String, ExternalJobEntity> jobStore = new HashMap<>();

    /**
     * 初始化 Mock 行为并模拟异步执行。
     */
    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            ExternalJobEntity entity = invocation.getArgument(0, ExternalJobEntity.class);
            jobStore.put(entity.getId(), entity);
            return entity;
        }).when(externalJobRepository).save(any(ExternalJobEntity.class));
        when(externalJobRepository.findById(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0, String.class);
            return Optional.ofNullable(jobStore.get(id));
        });
        // 模拟调度器立即执行任务
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1, Runnable.class);
            runnable.run();
            return null;
        }).when(taskScheduler).submit(anyString(), any(Runnable.class));
    }

    /**
     * 验证模型任务能够成功完成并返回结果。
     */
    @Test
    void submitModelJob_shouldCompleteAndExposeResult() {
        when(taskService.submitTask(any())).thenReturn("task-001");
        when(taskService.getTask("task-001")).thenReturn(buildTaskVo("task-001", "SUCCESS"));

        ExternalModelJobRequest request = new ExternalModelJobRequest();
        request.setRuleId(1L);
        TaskSubmitRequest.TimeRange range = new TaskSubmitRequest.TimeRange();
        range.setStart(LocalDateTime.of(2026, 3, 1, 0, 0, 0));
        range.setEnd(LocalDateTime.of(2026, 3, 1, 1, 0, 0));
        request.setTimeRange(range);
        request.setPollIntervalSeconds(1);
        request.setTimeoutSeconds(30);

        String jobId = externalJobService.submitModelJob(request, "trace-model").getJobId();
        ExternalJobStatusResponse status = externalJobService.getJobStatus(jobId);
        ExternalJobResultResponse result = externalJobService.getJobResult(jobId);

        assertEquals("SUCCEEDED", status.getStatus());
        assertNotNull(result.getResult());
        assertEquals("SUCCEEDED", result.getStatus());
    }

    /**
     * 验证算法任务参数不合法时会失败。
     */
    @Test
    void submitAlgorithmJob_shouldMarkFailedWhenPayloadInvalid() {
        ExternalAlgorithmJobRequest request = new ExternalAlgorithmJobRequest();
        request.setAction("TASK_COMPARE");

        String jobId = externalJobService.submitAlgorithmJob(request, "trace-algo").getJobId();
        ExternalJobStatusResponse status = externalJobService.getJobStatus(jobId);

        assertEquals("FAILED", status.getStatus());
        assertNotNull(status.getError());
        assertEquals("INVALID_ARGUMENT", status.getError().getCode());
    }

    /**
     * 验证数据导出任务强制同步并返回下载地址。
     */
    @Test
    void submitDataExportJob_shouldForceSyncAndExposeDownloadUrl() {
        DataExportResultVO exportResult = new DataExportResultVO();
        exportResult.setStatus("SUCCESS");
        exportResult.setFileName("demo.csv");
        exportResult.setDownloadUrl("/api/v1/data/files/demo.csv");
        when(dataExportService.exportData(any(DataExportRequest.class))).thenReturn(exportResult);

        DataExportRequest request = new DataExportRequest();
        request.setType("STRUCTURED");
        request.setSourceId(2L);
        request.setFormat("CSV");
        request.setAsync(true);

        ExternalDataExportJobRequest externalRequest = new ExternalDataExportJobRequest();
        externalRequest.setExportRequest(request);
        String jobId = externalJobService.submitDataExportJob(externalRequest, "trace-export").getJobId();

        ArgumentCaptor<DataExportRequest> captor = ArgumentCaptor.forClass(DataExportRequest.class);
        verify(dataExportService).exportData(captor.capture());
        assertEquals(false, captor.getValue().getAsync());

        ExternalJobResultResponse result = externalJobService.getJobResult(jobId);
        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals("/api/v1/data/files/demo.csv", result.getDownloadUrl());
        assertTrue(result.getResult() instanceof Map);
    }

    /**
     * 构造任务视图对象。
     */
    private TaskVO buildTaskVo(String id, String status) {
        TaskVO taskVO = new TaskVO();
        taskVO.setId(id);
        taskVO.setStatus(status);
        taskVO.setResultLink("root.assoc_sys.results." + id);
        taskVO.setExecLog("ok");
        return taskVO;
    }
}
