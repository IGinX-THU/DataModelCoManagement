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
        assertEquals(2L, result.getStructuredResult().getPage().getTotal());
        assertEquals(2, result.getStructuredResult().getPage().getRecords().size());
        assertEquals(0L, result.getStructuredResult().getPage().getRecords().get(0).get("KEY"));
        assertEquals(5.399d, result.getStructuredResult().getPage().getRecords().get(0).get("result"));
    }

    /**
     * 结构化结果查看应支持分页，避免一次性返回整表。
     */
    @Test
    void queryTaskSeries_shouldPageStructuredRows() throws Exception {
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
        task.setExecutionSnapshot(objectMapper.writeValueAsString(snapshot));

        SessionQueryDataSet lastDataSet = org.mockito.Mockito.mock(SessionQueryDataSet.class);
        when(lastDataSet.getKeys()).thenReturn(new long[]{2L});

        SessionQueryDataSet pageDataSet = org.mockito.Mockito.mock(SessionQueryDataSet.class);
        when(pageDataSet.getKeys()).thenReturn(new long[]{1L});
        when(pageDataSet.getValues()).thenReturn(List.of(List.of(5.419d)));
        when(pageDataSet.getPaths()).thenReturn(List.of("task.result.demo.result"));

        when(taskRepository.findById("demo-task")).thenReturn(Optional.of(task));
        when(associationRuleRepository.findById(1L)).thenReturn(Optional.empty());
        when(iginxStorageWrapper.executeWithSession(any())).thenReturn(lastDataSet, pageDataSet);

        TaskSeriesRequest request = new TaskSeriesRequest();
        request.setPageNum(2);
        request.setPageSize(1);
        TaskAnalysisResultVO result = service.queryTaskSeries("demo-task", request);

        assertEquals("STRUCTURED", result.getAnalysisMode());
        assertEquals(3L, result.getStructuredResult().getPage().getTotal());
        assertEquals(2, result.getStructuredResult().getPage().getPageNum());
        assertEquals(1, result.getStructuredResult().getPage().getPageSize());
        assertEquals(1, result.getStructuredResult().getPage().getRecords().size());
        assertEquals(1L, result.getStructuredResult().getPage().getRecords().get(0).get("KEY"));
        assertEquals(5.419d, result.getStructuredResult().getPage().getRecords().get(0).get("result"));
    }

    /**
     * 结构化图表查询应支持返回完整结果集，避免图表只基于当前页绘制。
     */
    @Test
    void queryTaskSeries_shouldReturnFullStructuredChartRowsWhenRequested() throws Exception {
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
        task.setExecutionSnapshot(objectMapper.writeValueAsString(snapshot));

        SessionQueryDataSet lastDataSet = org.mockito.Mockito.mock(SessionQueryDataSet.class);
        when(lastDataSet.getKeys()).thenReturn(new long[]{2L});

        SessionQueryDataSet fullChartDataSet = org.mockito.Mockito.mock(SessionQueryDataSet.class);
        when(fullChartDataSet.getKeys()).thenReturn(new long[]{0L, 1L, 2L});
        when(fullChartDataSet.getValues()).thenReturn(List.of(
            List.of(5.399d),
            List.of(5.419d),
            List.of(5.521d)
        ));
        when(fullChartDataSet.getPaths()).thenReturn(List.of("task.result.demo.result"));

        when(taskRepository.findById("demo-task")).thenReturn(Optional.of(task));
        when(associationRuleRepository.findById(1L)).thenReturn(Optional.empty());
        when(iginxStorageWrapper.executeWithSession(any())).thenReturn(lastDataSet, fullChartDataSet);

        TaskSeriesRequest request = new TaskSeriesRequest();
        request.setIncludeChartData(true);
        request.setIncludePageData(false);
        TaskAnalysisResultVO result = service.queryTaskSeries("demo-task", request);

        assertEquals("STRUCTURED", result.getAnalysisMode());
        assertTrue(result.getStructuredResult().getPage() == null);
        assertEquals(3, result.getStructuredResult().getChartRows().size());
        assertEquals(0L, result.getStructuredResult().getChartRows().get(0).get("KEY"));
        assertEquals(5.521d, result.getStructuredResult().getChartRows().get(2).get("result"));
    }

    /**
     * 当 IGinX 原生降采样不兼容时，任务结果分析应回退到原始点查询并在服务端本地聚合。
     */
    @Test
    void queryTaskSeries_shouldFallbackToLocalDownsampleWhenIginxDownsampleUnsupported() throws Exception {
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
        input.setResolvedPath("ts.factory.boiler.temperature");
        input.setPathKind("TS");

        TaskExecutionBinding output = new TaskExecutionBinding();
        output.setName("prediction");
        output.setResolvedPath("task.result.demo.prediction");

        TaskExecutionSnapshot snapshot = new TaskExecutionSnapshot();
        snapshot.setInputs(List.of(input));
        snapshot.setOutputs(List.of(output));

        TaskEntity task = new TaskEntity();
        task.setId("demo-task");
        task.setRuleId(1L);
        task.setRangeStart(LocalDateTime.of(2026, 4, 2, 16, 30, 0));
        task.setRangeEnd(LocalDateTime.of(2026, 4, 2, 16, 30, 4));
        task.setExecutionSnapshot(objectMapper.writeValueAsString(snapshot));

        when(taskRepository.findById("demo-task")).thenReturn(Optional.of(task));
        when(associationRuleRepository.findById(1L)).thenReturn(Optional.empty());

        long firstKey = toNano(task.getRangeStart());
        SessionQueryDataSet rawDataSet = org.mockito.Mockito.mock(SessionQueryDataSet.class);
        when(rawDataSet.getKeys()).thenReturn(new long[]{
            firstKey,
            firstKey + 1_000_000_000L,
            firstKey + 2_000_000_000L,
            firstKey + 3_000_000_000L
        });
        when(rawDataSet.getValues()).thenReturn(List.of(
            List.of(1.0d),
            List.of(3.0d),
            List.of(5.0d),
            List.of(7.0d)
        ));
        when(rawDataSet.getPaths()).thenReturn(List.of("task.result.demo.prediction"));

        when(iginxStorageWrapper.executeWithSession(any()))
            .thenThrow(BizException.badRequest("encounter error when execute set mapping function avg."))
            .thenReturn(rawDataSet);

        TaskSeriesRequest request = new TaskSeriesRequest();
        request.setDownsample(true);
        request.setAggregator("AVG");
        request.setPrecisionMs(2000L);

        TaskAnalysisResultVO result = service.queryTaskSeries("demo-task", request);

        assertEquals("TIME_SERIES", result.getAnalysisMode());
        assertEquals(1, result.getSeries().size());
        assertEquals(2, result.getSeries().get(0).getPoints().size());
        assertEquals(2.0d, result.getSeries().get(0).getPoints().get(0).getValue());
        assertEquals(6.0d, result.getSeries().get(0).getPoints().get(1).getValue());
        assertEquals(task.getRangeStart().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            result.getSeries().get(0).getPoints().get(0).getTimestamp());
    }

    /**
     * 当 IGinX 返回带聚合函数包装的路径名时，任务结果分析仍应正确匹配到对应输出。
     */
    @Test
    void queryTaskSeries_shouldMatchAggregatedPathsReturnedByIginx() throws Exception {
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
        input.setResolvedPath("ts.factory.boiler.temperature");
        input.setPathKind("TS");

        TaskExecutionBinding output = new TaskExecutionBinding();
        output.setName("prediction");
        output.setResolvedPath("task.result.demo.prediction");

        TaskExecutionSnapshot snapshot = new TaskExecutionSnapshot();
        snapshot.setInputs(List.of(input));
        snapshot.setOutputs(List.of(output));

        TaskEntity task = new TaskEntity();
        task.setId("demo-task");
        task.setRuleId(1L);
        task.setRangeStart(LocalDateTime.of(2026, 4, 2, 16, 30, 0));
        task.setRangeEnd(LocalDateTime.of(2026, 4, 2, 16, 30, 4));
        task.setExecutionSnapshot(objectMapper.writeValueAsString(snapshot));

        long firstKey = toNano(task.getRangeStart());
        SessionQueryDataSet downsampled = org.mockito.Mockito.mock(SessionQueryDataSet.class);
        when(downsampled.getKeys()).thenReturn(new long[]{
            firstKey,
            firstKey + 2_000_000_000L
        });
        when(downsampled.getValues()).thenReturn(List.of(
            List.of(3.0d),
            List.of(7.0d)
        ));
        when(downsampled.getPaths()).thenReturn(List.of("MAX(task.result.demo.prediction)"));

        when(taskRepository.findById("demo-task")).thenReturn(Optional.of(task));
        when(associationRuleRepository.findById(1L)).thenReturn(Optional.empty());
        when(iginxStorageWrapper.executeWithSession(any())).thenReturn(downsampled);

        TaskSeriesRequest request = new TaskSeriesRequest();
        request.setDownsample(true);
        request.setAggregator("MAX");
        request.setPrecisionMs(2000L);

        TaskAnalysisResultVO result = service.queryTaskSeries("demo-task", request);

        assertEquals("TIME_SERIES", result.getAnalysisMode());
        assertEquals(1, result.getSeries().size());
        assertEquals(2, result.getSeries().get(0).getPoints().size());
        assertEquals(3.0d, result.getSeries().get(0).getPoints().get(0).getValue());
        assertEquals(7.0d, result.getSeries().get(0).getPoints().get(1).getValue());
    }

    /**
     * 当 IGinX 降采样返回的“聚合值”实际等于纳秒时间戳时，任务结果分析应回退到本地聚合。
     */
    @Test
    void queryTaskSeries_shouldFallbackWhenDownsampledValuesLookLikeTimestamps() throws Exception {
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
        input.setResolvedPath("ts.factory.boiler.temperature");
        input.setPathKind("TS");

        TaskExecutionBinding output = new TaskExecutionBinding();
        output.setName("prediction");
        output.setResolvedPath("task.result.demo.prediction");

        TaskExecutionSnapshot snapshot = new TaskExecutionSnapshot();
        snapshot.setInputs(List.of(input));
        snapshot.setOutputs(List.of(output));

        TaskEntity task = new TaskEntity();
        task.setId("demo-task");
        task.setRuleId(1L);
        task.setRangeStart(LocalDateTime.of(2026, 4, 2, 16, 30, 0));
        task.setRangeEnd(LocalDateTime.of(2026, 4, 2, 16, 30, 4));
        task.setExecutionSnapshot(objectMapper.writeValueAsString(snapshot));

        long firstKey = toNano(task.getRangeStart());
        SessionQueryDataSet downsampled = org.mockito.Mockito.mock(SessionQueryDataSet.class);
        when(downsampled.getKeys()).thenReturn(new long[]{
            firstKey,
            firstKey + 2_000_000_000L
        });
        when(downsampled.getValues()).thenReturn(List.of(
            List.of(firstKey),
            List.of(firstKey + 2_000_000_000L)
        ));

        SessionQueryDataSet rawDataSet = org.mockito.Mockito.mock(SessionQueryDataSet.class);
        when(rawDataSet.getKeys()).thenReturn(new long[]{
            firstKey,
            firstKey + 1_000_000_000L,
            firstKey + 2_000_000_000L,
            firstKey + 3_000_000_000L
        });
        when(rawDataSet.getValues()).thenReturn(List.of(
            List.of(1.0d),
            List.of(3.0d),
            List.of(5.0d),
            List.of(7.0d)
        ));
        when(rawDataSet.getPaths()).thenReturn(List.of("task.result.demo.prediction"));

        when(taskRepository.findById("demo-task")).thenReturn(Optional.of(task));
        when(associationRuleRepository.findById(1L)).thenReturn(Optional.empty());
        when(iginxStorageWrapper.executeWithSession(any())).thenReturn(downsampled, rawDataSet);

        TaskSeriesRequest request = new TaskSeriesRequest();
        request.setDownsample(true);
        request.setAggregator("COUNT");
        request.setPrecisionMs(2000L);

        TaskAnalysisResultVO result = service.queryTaskSeries("demo-task", request);

        assertEquals("TIME_SERIES", result.getAnalysisMode());
        assertEquals(1, result.getSeries().size());
        assertEquals(2, result.getSeries().get(0).getPoints().size());
        assertEquals(2.0d, result.getSeries().get(0).getPoints().get(0).getValue());
        assertEquals(2.0d, result.getSeries().get(0).getPoints().get(1).getValue());
    }

    /**
     * 当降采样结果行前面携带时间列时，任务结果分析应读取真实的聚合值列。
     */
    @Test
    void queryTaskSeries_shouldReadAggregatedValueColumnWhenRowsContainLeadingTimestamp() throws Exception {
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
        input.setResolvedPath("ts.factory.boiler.temperature");
        input.setPathKind("TS");

        TaskExecutionBinding output = new TaskExecutionBinding();
        output.setName("prediction");
        output.setResolvedPath("task.result.demo.prediction");

        TaskExecutionSnapshot snapshot = new TaskExecutionSnapshot();
        snapshot.setInputs(List.of(input));
        snapshot.setOutputs(List.of(output));

        TaskEntity task = new TaskEntity();
        task.setId("demo-task");
        task.setRuleId(1L);
        task.setRangeStart(LocalDateTime.of(2026, 4, 2, 16, 30, 0));
        task.setRangeEnd(LocalDateTime.of(2026, 4, 2, 16, 30, 4));
        task.setExecutionSnapshot(objectMapper.writeValueAsString(snapshot));

        long firstKey = toNano(task.getRangeStart());
        SessionQueryDataSet downsampled = org.mockito.Mockito.mock(SessionQueryDataSet.class);
        when(downsampled.getKeys()).thenReturn(new long[]{
            firstKey,
            firstKey + 2_000_000_000L
        });
        when(downsampled.getValues()).thenReturn(List.of(
            List.of(firstKey, 30.0d),
            List.of(firstKey + 2_000_000_000L, 50.0d)
        ));
        when(downsampled.getPaths()).thenReturn(List.of("MAX(task.result.demo.prediction)"));

        when(taskRepository.findById("demo-task")).thenReturn(Optional.of(task));
        when(associationRuleRepository.findById(1L)).thenReturn(Optional.empty());
        when(iginxStorageWrapper.executeWithSession(any())).thenReturn(downsampled);

        TaskSeriesRequest request = new TaskSeriesRequest();
        request.setDownsample(true);
        request.setAggregator("MAX");
        request.setPrecisionMs(2000L);

        TaskAnalysisResultVO result = service.queryTaskSeries("demo-task", request);

        assertEquals("TIME_SERIES", result.getAnalysisMode());
        assertEquals(1, result.getSeries().size());
        assertEquals(2, result.getSeries().get(0).getPoints().size());
        assertEquals(30.0d, result.getSeries().get(0).getPoints().get(0).getValue());
        assertEquals(50.0d, result.getSeries().get(0).getPoints().get(1).getValue());
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

    /**
     * 将时间转换为纳秒，保持与生产代码一致的时区语义。
     */
    private long toNano(LocalDateTime time) {
        long millis = time.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        return millis * 1_000_000L;
    }
}
