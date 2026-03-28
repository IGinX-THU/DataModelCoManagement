package com.xmu.iginx.assoc.modules.analysis.service.impl;

import cn.edu.tsinghua.iginx.session.QueryDataSet;
import cn.edu.tsinghua.iginx.session.SessionQueryDataSet;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskExportRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskReportRequest;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskCompareRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskSeriesRequest;
import com.xmu.iginx.assoc.modules.analysis.vo.TaskAnalysisResultVO;
import com.xmu.iginx.assoc.modules.data.util.DataFileStorageService;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredQueryHelper;
import com.xmu.iginx.assoc.modules.model.repository.ModelAssetRepository;
import com.xmu.iginx.assoc.modules.model.util.ModelFileStorageService;
import com.xmu.iginx.assoc.modules.relation.entity.AssociationRuleEntity;
import com.xmu.iginx.assoc.modules.relation.repository.AssociationRuleRepository;
import com.xmu.iginx.assoc.modules.task.entity.TaskEntity;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionBinding;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionSnapshot;
import com.xmu.iginx.assoc.modules.task.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 分析服务测试。
 */
@ExtendWith(MockitoExtension.class)
class AnalysisServiceImplTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private AssociationRuleRepository associationRuleRepository;

    @Mock
    private IginxStorageWrapper iginxStorageWrapper;

    @Mock
    private ModelAssetRepository modelAssetRepository;

    @Mock
    private ModelFileStorageService modelFileStorageService;

    @Mock
    private DataFileStorageService dataFileStorageService;

    @Mock
    private IginxStructuredQueryHelper structuredQueryHelper;

    /**
     * 纯结构化输入任务应返回结构化结果表，而不是折线图点集。
     */
    @Test
    void queryTaskSeries_shouldReturnStructuredRowsForStructuredTask() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AnalysisServiceImpl service = new AnalysisServiceImpl(
            taskRepository,
            associationRuleRepository,
            iginxStorageWrapper,
            objectMapper,
            modelAssetRepository,
            modelFileStorageService,
            dataFileStorageService,
            structuredQueryHelper
        );

        TaskExecutionBinding input = new TaskExecutionBinding();
        input.setName("temperature");
        input.setResolvedPath("rt.factory.boiler.temperature");
        input.setPathKind("RT");

        TaskExecutionBinding output = new TaskExecutionBinding();
        output.setName("result");
        output.setResolvedPath("task.result.demo.result");

        TaskExecutionSnapshot snapshot = new TaskExecutionSnapshot();
        snapshot.setInputs(List.of(input));
        snapshot.setOutputs(List.of(output));

        TaskEntity task = new TaskEntity();
        task.setId("demo-task");
        task.setRuleId(1L);
        task.setRangeStart(null);
        task.setRangeEnd(null);
        task.setCreateTime(LocalDateTime.of(2026, 3, 27, 18, 0, 0));
        task.setExecutionSnapshot(objectMapper.writeValueAsString(snapshot));

        SessionQueryDataSet dataSet = org.mockito.Mockito.mock(SessionQueryDataSet.class);
        when(dataSet.getKeys()).thenReturn(new long[]{0L, 1L});
        when(dataSet.getValues()).thenReturn(List.of(
            List.of(5.399d),
            List.of(5.419d)
        ));
        when(dataSet.getPaths()).thenReturn(List.of("task.result.demo.result"));

        when(taskRepository.findById("demo-task")).thenReturn(Optional.of(task));
        when(associationRuleRepository.findById(1L)).thenReturn(Optional.empty());
        when(iginxStorageWrapper.executeWithSession(any())).thenReturn(dataSet);

        TaskAnalysisResultVO result = service.queryTaskSeries("demo-task", new TaskSeriesRequest());

        assertEquals("STRUCTURED", result.getAnalysisMode());
        assertTrue(result.getSeries() == null || result.getSeries().isEmpty());
        assertEquals(List.of("KEY", "result"), result.getStructuredResult().getColumns());
        assertEquals(2, result.getStructuredResult().getRows().size());
        assertEquals(0L, result.getStructuredResult().getRows().get(0).get("KEY"));
        assertEquals(5.399d, result.getStructuredResult().getRows().get(0).get("result"));
    }

    /**
     * 结构化输入任务不允许参与多任务折线图对比。
     */
    @Test
    void compareTasks_shouldRejectStructuredTask() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AnalysisServiceImpl service = new AnalysisServiceImpl(
            taskRepository,
            associationRuleRepository,
            iginxStorageWrapper,
            objectMapper,
            modelAssetRepository,
            modelFileStorageService,
            dataFileStorageService,
            structuredQueryHelper
        );

        TaskExecutionBinding input = new TaskExecutionBinding();
        input.setName("temperature");
        input.setResolvedPath("rt.factory.boiler.temperature");
        input.setPathKind("RT");

        TaskExecutionSnapshot snapshot = new TaskExecutionSnapshot();
        snapshot.setInputs(List.of(input));

        TaskEntity task = new TaskEntity();
        task.setId("demo-task");
        task.setRuleId(1L);
        task.setExecutionSnapshot(objectMapper.writeValueAsString(snapshot));

        when(taskRepository.findById("demo-task")).thenReturn(Optional.of(task));

        TaskCompareRequest request = new TaskCompareRequest();
        request.setTaskIds(List.of("demo-task"));
        request.setMode("absolute");

        BizException exception = assertThrows(BizException.class, () -> service.compareTasks(request));
        assertTrue(exception.getMessage().contains("结构化输入任务仅支持单独查看结果表"));
    }

    /**
     * 结构化输入任务导出资源包时，输入输出都应按表格而不是时序 CSV 导出。
     */
    @Test
    void exportPackage_shouldExportStructuredTaskAsTables() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AnalysisServiceImpl service = new AnalysisServiceImpl(
            taskRepository,
            associationRuleRepository,
            iginxStorageWrapper,
            objectMapper,
            modelAssetRepository,
            modelFileStorageService,
            dataFileStorageService,
            structuredQueryHelper
        );

        TaskExecutionSnapshot snapshot = buildStructuredSnapshot();
        TaskEntity task = buildStructuredTask(objectMapper, snapshot);
        AssociationRuleEntity rule = buildRule();

        QueryDataSet inputSet = org.mockito.Mockito.mock(QueryDataSet.class);
        when(inputSet.getColumnList()).thenReturn(List.of("temperature", "pressure", "flow"));
        when(inputSet.nextRow()).thenReturn(
            new Object[]{18.85d, 1.08d, 1.05d},
            new Object[]{18.95d, 1.08d, 1.05d},
            (Object[]) null
        );

        SessionQueryDataSet outputSet = org.mockito.Mockito.mock(SessionQueryDataSet.class);
        when(outputSet.getKeys()).thenReturn(new long[]{0L, 1L});
        when(outputSet.getValues()).thenReturn(List.of(
            List.of(5.399d),
            List.of(5.419d)
        ));
        when(outputSet.getPaths()).thenReturn(List.of("task.result.demo.result"));

        Path zipPath = Files.createTempFile("analysis-structured-", ".zip");
        when(taskRepository.findById("demo-task")).thenReturn(Optional.of(task));
        when(associationRuleRepository.findById(1L)).thenReturn(Optional.of(rule));
        when(structuredQueryHelper.executeQuery(anyString(), anyInt())).thenReturn(inputSet);
        when(iginxStorageWrapper.executeWithSession(any())).thenReturn(outputSet);
        when(dataFileStorageService.createFile("analysis_package", ".zip"))
            .thenReturn(new DataFileStorageService.StoredFile("analysis_package_test.zip", zipPath));

        TaskExportRequest request = new TaskExportRequest();
        request.setIncludeInput(true);
        request.setIncludeOutput(true);
        request.setIncludeModel(false);
        request.setFormat("CSV");

        String downloadPath = service.exportPackage("demo-task", request);

        assertTrue(downloadPath.endsWith("analysis_package_test.zip"));
        Map<String, String> entries = readZipEntries(zipPath);
        assertTrue(entries.get("metadata/task.json").contains("\"analysisMode\":\"STRUCTURED\""));
        assertTrue(entries.get("data/input.csv").startsWith("KEY,temperature,pressure,flow"));
        assertTrue(entries.get("data/input.csv").contains("0,18.85,1.08,1.05"));
        assertTrue(entries.get("data/output.csv").startsWith("KEY,result"));
        assertTrue(entries.get("data/output.csv").contains("0,5.399"));
    }

    /**
     * 结构化输入任务生成报告时，应输出结构化结果预览，而不是时序图表。
     */
    @Test
    void generateReport_shouldUseStructuredPreviewForStructuredTask() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AnalysisServiceImpl service = new AnalysisServiceImpl(
            taskRepository,
            associationRuleRepository,
            iginxStorageWrapper,
            objectMapper,
            modelAssetRepository,
            modelFileStorageService,
            dataFileStorageService,
            structuredQueryHelper
        );

        TaskExecutionSnapshot snapshot = buildStructuredSnapshot();
        TaskEntity task = buildStructuredTask(objectMapper, snapshot);
        AssociationRuleEntity rule = buildRule();

        QueryDataSet inputSet = org.mockito.Mockito.mock(QueryDataSet.class);
        when(inputSet.getColumnList()).thenReturn(List.of("temperature", "pressure", "flow"));
        when(inputSet.nextRow()).thenReturn(
            new Object[]{18.85d, 1.08d, 1.05d},
            new Object[]{18.95d, 1.08d, 1.05d},
            (Object[]) null
        );

        SessionQueryDataSet outputSet = org.mockito.Mockito.mock(SessionQueryDataSet.class);
        when(outputSet.getKeys()).thenReturn(new long[]{0L, 1L});
        when(outputSet.getValues()).thenReturn(List.of(
            List.of(5.399d),
            List.of(5.419d)
        ));
        when(outputSet.getPaths()).thenReturn(List.of("task.result.demo.result"));

        Path pdfPath = Files.createTempFile("analysis-structured-report-", ".pdf");
        when(taskRepository.findById("demo-task")).thenReturn(Optional.of(task));
        when(associationRuleRepository.findById(1L)).thenReturn(Optional.of(rule));
        when(structuredQueryHelper.executeQuery(anyString(), anyInt())).thenReturn(inputSet);
        when(iginxStorageWrapper.executeWithSession(any())).thenReturn(outputSet);
        when(dataFileStorageService.createFile("task_report", ".pdf"))
            .thenReturn(new DataFileStorageService.StoredFile("task_report_test.pdf", pdfPath));

        TaskReportRequest request = new TaskReportRequest();
        request.setIncludeStats(true);
        request.setIncludeCharts(true);

        String downloadPath = service.generateReport("demo-task", request);

        assertTrue(downloadPath.endsWith("task_report_test.pdf"));
        byte[] pdfBytes = Files.readAllBytes(pdfPath);
        String pdfText = new String(pdfBytes, StandardCharsets.ISO_8859_1);
        assertTrue(pdfBytes.length > 0);
        assertTrue(pdfText.contains("Structured Result Preview"));
    }

    private TaskExecutionSnapshot buildStructuredSnapshot() {
        TaskExecutionBinding temperature = new TaskExecutionBinding();
        temperature.setName("temperature");
        temperature.setResolvedPath("rt.factory.power.temperature");
        temperature.setPathKind("RT");

        TaskExecutionBinding pressure = new TaskExecutionBinding();
        pressure.setName("pressure");
        pressure.setResolvedPath("rt.factory.power.pressure");
        pressure.setPathKind("RT");

        TaskExecutionBinding flow = new TaskExecutionBinding();
        flow.setName("flow");
        flow.setResolvedPath("rt.factory.power.flow");
        flow.setPathKind("RT");

        TaskExecutionBinding output = new TaskExecutionBinding();
        output.setName("result");
        output.setResolvedPath("task.result.demo.result");

        TaskExecutionSnapshot snapshot = new TaskExecutionSnapshot();
        snapshot.setInputs(List.of(temperature, pressure, flow));
        snapshot.setOutputs(List.of(output));
        return snapshot;
    }

    private TaskEntity buildStructuredTask(ObjectMapper objectMapper, TaskExecutionSnapshot snapshot) throws Exception {
        TaskEntity task = new TaskEntity();
        task.setId("demo-task");
        task.setRuleId(1L);
        task.setRangeStart(null);
        task.setRangeEnd(null);
        task.setResultLink("task.result.demo");
        task.setCreateTime(LocalDateTime.of(2026, 3, 27, 18, 0, 0));
        task.setExecutionSnapshot(objectMapper.writeValueAsString(snapshot));
        return task;
    }

    private AssociationRuleEntity buildRule() {
        AssociationRuleEntity rule = new AssociationRuleEntity();
        rule.setId(1L);
        rule.setName("结构化功率预测");
        rule.setModelId(100L);
        return rule;
    }

    private Map<String, String> readZipEntries(Path zipPath) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipPath), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return entries;
    }
}
