package com.xmu.iginx.assoc.modules.data.service.impl;

import cn.edu.tsinghua.iginx.session.QueryDataSet;
import cn.edu.tsinghua.iginx.thrift.DataType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.dto.DataExportRequest;
import com.xmu.iginx.assoc.modules.data.entity.DataResourceEntity;
import com.xmu.iginx.assoc.modules.data.enums.DataSourceType;
import com.xmu.iginx.assoc.modules.data.model.DataSourceDetail;
import com.xmu.iginx.assoc.modules.data.repository.DataExportTaskRepository;
import com.xmu.iginx.assoc.modules.data.service.DataSourceAccessor;
import com.xmu.iginx.assoc.modules.data.util.DataFileStorageService;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredQueryHelper;
import com.xmu.iginx.assoc.modules.data.vo.DataExportResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataExportServiceImplTest {

    @Mock
    private DataExportTaskRepository taskRepository;
    @Mock
    private DataFileStorageService fileStorageService;
    @Mock
    private DataSourceAccessor dataSourceAccessor;
    @Mock
    private IginxStorageWrapper iginxStorageWrapper;
    @Mock
    private IginxStructuredQueryHelper structuredQueryHelper;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private DataExportServiceImpl dataExportService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        when(dataSourceAccessor.getDetail(anyLong(), any(DataSourceType[].class)))
            .thenReturn(buildStructuredSourceDetail());
    }

    @Test
    void exportData_shouldOnlyOutputSelectedColumnsForStructuredCsv() throws Exception {
        DataExportRequest request = buildStructuredRequest();
        request.setColumns(List.of("pressure"));

        Path exportPath = tempDir.resolve("selected-columns.csv");
        when(fileStorageService.createFile(anyString(), eq(".csv")))
            .thenReturn(new DataFileStorageService.StoredFile("selected-columns.csv", exportPath));

        when(structuredQueryHelper.loadColumnTypes(anyString(), eq("demo_table")))
            .thenReturn(buildColumnTypes());

        QueryDataSet dataSet = mock(QueryDataSet.class);
        when(structuredQueryHelper.executeQuery(anyString(), anyInt())).thenReturn(dataSet);
        when(dataSet.getColumnList()).thenReturn(List.of("KEY", "temperature", "pressure", "status"));
        when(dataSet.nextRow())
            .thenReturn(new Object[]{1L, 21.5d, 101.3d, "ok".getBytes(StandardCharsets.UTF_8)})
            .thenReturn(new Object[]{2L, 22.0d, null, "running".getBytes(StandardCharsets.UTF_8)})
            .thenReturn(new Object[]{3L, null, null, null})
            .thenThrow(new RuntimeException("END"));

        DataExportResultVO result = dataExportService.exportData(request);

        List<String> lines = Files.readAllLines(exportPath, StandardCharsets.UTF_8);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("\uFEFFpressure", lines.get(0));
        assertEquals("101.3", lines.get(1));
        assertEquals("", lines.get(2));
        assertEquals(3, lines.size());
    }

    @Test
    void exportData_shouldRejectUnknownStructuredColumns() {
        DataExportRequest request = buildStructuredRequest();
        request.setColumns(List.of("missing_column"));

        Path exportPath = tempDir.resolve("invalid-columns.csv");
        when(fileStorageService.createFile(anyString(), eq(".csv")))
            .thenReturn(new DataFileStorageService.StoredFile("invalid-columns.csv", exportPath));
        when(structuredQueryHelper.loadColumnTypes(anyString(), eq("demo_table")))
            .thenReturn(buildColumnTypes());

        BizException exception = assertThrows(BizException.class, () -> dataExportService.exportData(request));

        assertTrue(exception.getMessage().contains("Export column not found"));
        verify(structuredQueryHelper, never()).executeQuery(anyString(), anyInt());
    }

    @Test
    void exportData_shouldFilterSelectedColumnsWhenSqlProvided() throws Exception {
        DataExportRequest request = buildStructuredRequest();
        request.setSql("SELECT * FROM root.demo.public.demo_table");
        request.setColumns(List.of("status"));

        Path exportPath = tempDir.resolve("sql-selected-columns.csv");
        when(fileStorageService.createFile(anyString(), eq(".csv")))
            .thenReturn(new DataFileStorageService.StoredFile("sql-selected-columns.csv", exportPath));

        QueryDataSet dataSet = mock(QueryDataSet.class);
        when(structuredQueryHelper.executeQuery(anyString(), anyInt())).thenReturn(dataSet);
        when(dataSet.getColumnList()).thenReturn(List.of("KEY", "temperature", "pressure", "status"));
        when(dataSet.nextRow())
            .thenReturn(new Object[]{1L, 21.5d, 101.3d, "ok".getBytes(StandardCharsets.UTF_8)})
            .thenThrow(new RuntimeException("END"));

        DataExportResultVO result = dataExportService.exportData(request);

        List<String> lines = Files.readAllLines(exportPath, StandardCharsets.UTF_8);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("\uFEFFstatus", lines.get(0));
        assertEquals("ok", lines.get(1));
        assertEquals(2, lines.size());
        verify(structuredQueryHelper, never()).loadColumnTypes(anyString(), anyString());
    }

    private DataExportRequest buildStructuredRequest() {
        DataExportRequest request = new DataExportRequest();
        request.setType("STRUCT");
        request.setSourceId(1L);
        request.setFormat("CSV");
        request.setSchema("public");
        request.setTable("demo_table");
        request.setAsync(false);
        return request;
    }

    private Map<String, DataType> buildColumnTypes() {
        Map<String, DataType> columnTypes = new LinkedHashMap<>();
        columnTypes.put("temperature", DataType.DOUBLE);
        columnTypes.put("pressure", DataType.DOUBLE);
        columnTypes.put("status", DataType.BINARY);
        return columnTypes;
    }

    private DataSourceDetail buildStructuredSourceDetail() {
        DataResourceEntity entity = new DataResourceEntity();
        entity.setId(1L);
        entity.setSourceType("POSTGRESQL");
        entity.setMountPath("root.demo");
        return new DataSourceDetail(entity, DataSourceType.POSTGRESQL, null);
    }
}
