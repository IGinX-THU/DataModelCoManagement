package com.xmu.iginx.assoc.modules.data.service.impl;

import cn.edu.tsinghua.iginx.session.QueryDataSet;
import cn.edu.tsinghua.iginx.session.Session;
import cn.edu.tsinghua.iginx.session.SessionQueryDataSet;
import cn.edu.tsinghua.iginx.thrift.AggregateType;
import cn.edu.tsinghua.iginx.thrift.DataType;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.dto.StructuredQueryCondition;
import com.xmu.iginx.assoc.modules.data.dto.StructuredQueryRequest;
import com.xmu.iginx.assoc.modules.data.dto.TimeRangeDTO;
import com.xmu.iginx.assoc.modules.data.dto.TimeSeriesQueryRequest;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredQueryHelper;
import com.xmu.iginx.assoc.modules.data.vo.StructuredQueryResultVO;
import com.xmu.iginx.assoc.modules.data.vo.StructuredSchemaVO;
import com.xmu.iginx.assoc.modules.data.vo.TimeSeriesQueryResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

/**
 * 时序查询服务测试。
 */
@ExtendWith(MockitoExtension.class)
class DataQueryServiceImplTest {

    @Mock
    private IginxStructuredQueryHelper structuredQueryHelper;

    @Mock
    private IginxStorageWrapper iginxStorageWrapper;

    @Mock
    private Session session;

    @Mock
    private SessionQueryDataSet queryDataSet;

    @InjectMocks
    private DataQueryServiceImpl dataQueryService;

    /**
     * 将 executeWithSession 的回调转发到 mock Session，
     * 便于验证 queryData 调用时传入的集合是否可变。
     */
    @BeforeEach
    void setUp() {
        lenient().when(iginxStorageWrapper.executeWithSession(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            IginxStorageWrapper.SessionExecutor<Object> executor =
                (IginxStorageWrapper.SessionExecutor<Object>) invocation.getArgument(0);
            return executor.apply(session);
        });
    }

    /**
     * IGinX Session 在内部会对路径集合排序，
     * 这里通过主动排序来验证传入列表必须是可变集合。
     */
    @Test
    void queryTimeSeries_shouldUseMutablePathList() throws Exception {
        TimeSeriesQueryRequest request = new TimeSeriesQueryRequest();
        request.setPaths(List.of("root.demo.device_b", "root.demo.device_a"));
        TimeRangeDTO range = new TimeRangeDTO();
        range.setStart("0");
        range.setEnd("10");
        request.setTimeRange(range);

        when(session.queryData(any(), anyLong(), anyLong())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<String> paths = invocation.getArgument(0, List.class);
            // IGinX SDK 内部会排序路径，这里模拟同样行为以覆盖回归场景。
            Collections.sort(paths);
            return queryDataSet;
        });
        when(queryDataSet.getKeys()).thenReturn(new long[]{1_000_000_000L});
        when(queryDataSet.getPaths()).thenReturn(List.of("root.demo.device_a"));
        when(queryDataSet.getValues()).thenReturn(List.of(List.of(12.5d)));

        TimeSeriesQueryResultVO result = dataQueryService.queryTimeSeries(request);

        assertEquals(1, result.getTimestamps().size());
        assertEquals(1000L, result.getTimestamps().get(0));
        assertEquals(1, result.getSeries().size());
        assertEquals("root.demo.device_a", result.getSeries().get(0).getPath());
        assertEquals(12.5d, result.getSeries().get(0).getValues().get(0));
    }

    /**
     * 用户在界面上输入的结束时间应被视为“包含该时刻”，
     * 因此后端需要把结束时间转换成开区间上界后再传给 IGinX。
     */
    @Test
    void queryTimeSeries_shouldTreatEndTimeAsInclusive() throws Exception {
        TimeSeriesQueryRequest request = new TimeSeriesQueryRequest();
        request.setPaths(List.of("ts.demo.temperature"));
        TimeRangeDTO range = new TimeRangeDTO();
        range.setStart("2026-03-23 02:18:00");
        range.setEnd("2026-03-23 02:18:00");
        request.setTimeRange(range);

        when(session.queryData(any(), anyLong(), anyLong())).thenReturn(queryDataSet);
        when(queryDataSet.getKeys()).thenReturn(new long[]{1_774_203_480_000_000_000L});
        when(queryDataSet.getPaths()).thenReturn(List.of("ts.demo.temperature"));
        when(queryDataSet.getValues()).thenReturn(List.of(List.of(23.4d)));

        dataQueryService.queryTimeSeries(request);

        ArgumentCaptor<Long> startCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> endCaptor = ArgumentCaptor.forClass(Long.class);
        verify(session).queryData(any(), startCaptor.capture(), endCaptor.capture());
        assertEquals(1_774_203_480_000_000_000L, startCaptor.getValue());
        assertEquals(1_774_203_480_001_000_000L, endCaptor.getValue());
    }

    /**
     * 当 IGinX 原生降采样不兼容时，应回退到原始点查询并在服务端本地聚合。
     */
    @Test
    void queryTimeSeries_shouldFallbackToLocalDownsampleWhenIginxDownsampleUnsupported() throws Exception {
        TimeSeriesQueryRequest request = new TimeSeriesQueryRequest();
        request.setPaths(List.of("ts.demo.temperature"));
        request.setDownsample(true);
        request.setAggregator("AVG");
        request.setPrecisionMs(2000L);
        TimeRangeDTO range = new TimeRangeDTO();
        range.setStart("0");
        range.setEnd("4000");
        request.setTimeRange(range);

        when(session.downsampleQuery(any(), anyLong(), anyLong(), eq(AggregateType.AVG), anyLong()))
            .thenThrow(BizException.badRequest("encounter error when execute set mapping function avg."));
        when(session.queryData(any(), anyLong(), anyLong())).thenReturn(queryDataSet);
        when(queryDataSet.getKeys()).thenReturn(new long[]{
            0L,
            1_000_000_000L,
            2_000_000_000L,
            3_000_000_000L
        });
        when(queryDataSet.getPaths()).thenReturn(List.of("ts.demo.temperature"));
        when(queryDataSet.getValues()).thenReturn(List.of(
            List.of(1.0d),
            List.of(3.0d),
            List.of(5.0d),
            List.of(7.0d)
        ));

        TimeSeriesQueryResultVO result = dataQueryService.queryTimeSeries(request);

        assertEquals(List.of(0L, 2000L), result.getTimestamps());
        assertEquals(1, result.getSeries().size());
        assertEquals("ts.demo.temperature", result.getSeries().get(0).getPath());
        assertEquals(List.of(2.0d, 6.0d), result.getSeries().get(0).getValues());
        verify(session).downsampleQuery(any(), anyLong(), anyLong(), eq(AggregateType.AVG), anyLong());
        verify(session).queryData(any(), anyLong(), anyLong());
    }

    /**
     * 当 IGinX 降采样返回的“聚合值”实际等于纳秒时间戳时，应视为异常结果并回退到本地聚合。
     */
    @Test
    void queryTimeSeries_shouldFallbackWhenDownsampledValuesLookLikeTimestamps() throws Exception {
        TimeSeriesQueryRequest request = new TimeSeriesQueryRequest();
        request.setPaths(List.of("ts.demo.temperature"));
        request.setDownsample(true);
        request.setAggregator("COUNT");
        request.setPrecisionMs(2000L);
        TimeRangeDTO range = new TimeRangeDTO();
        range.setStart("0");
        range.setEnd("4000");
        request.setTimeRange(range);

        SessionQueryDataSet downsampledDataSet = org.mockito.Mockito.mock(SessionQueryDataSet.class);
        when(downsampledDataSet.getKeys()).thenReturn(new long[]{
            0L,
            2_000_000_000L
        });
        when(downsampledDataSet.getValues()).thenReturn(List.of(
            List.of(0L),
            List.of(2_000_000_000L)
        ));

        when(session.downsampleQuery(any(), anyLong(), anyLong(), eq(AggregateType.COUNT), anyLong()))
            .thenReturn(downsampledDataSet);
        when(session.queryData(any(), anyLong(), anyLong())).thenReturn(queryDataSet);
        when(queryDataSet.getKeys()).thenReturn(new long[]{
            0L,
            1_000_000_000L,
            2_000_000_000L,
            3_000_000_000L
        });
        when(queryDataSet.getPaths()).thenReturn(List.of("ts.demo.temperature"));
        when(queryDataSet.getValues()).thenReturn(List.of(
            List.of(11.0d),
            List.of(12.0d),
            List.of(13.0d),
            List.of(14.0d)
        ));

        TimeSeriesQueryResultVO result = dataQueryService.queryTimeSeries(request);

        assertEquals(List.of(0L, 2000L), result.getTimestamps());
        assertEquals(1, result.getSeries().size());
        assertEquals(List.of(2.0d, 2.0d), result.getSeries().get(0).getValues());
        verify(session).downsampleQuery(any(), anyLong(), anyLong(), eq(AggregateType.COUNT), anyLong());
        verify(session).queryData(any(), anyLong(), anyLong());
    }

    /**
     * 当降采样结果行前面携带时间列时，应读取真实的聚合值列，而不是把时间列当成数值返回。
     */
    @Test
    void queryTimeSeries_shouldReadAggregatedValueColumnWhenRowsContainLeadingTimestamp() throws Exception {
        TimeSeriesQueryRequest request = new TimeSeriesQueryRequest();
        request.setPaths(List.of("ts.demo.temperature"));
        request.setDownsample(true);
        request.setAggregator("MAX");
        request.setPrecisionMs(2000L);
        TimeRangeDTO range = new TimeRangeDTO();
        range.setStart("0");
        range.setEnd("4000");
        request.setTimeRange(range);

        SessionQueryDataSet downsampledDataSet = org.mockito.Mockito.mock(SessionQueryDataSet.class);
        when(downsampledDataSet.getKeys()).thenReturn(new long[]{
            0L,
            2_000_000_000L
        });
        when(downsampledDataSet.getPaths()).thenReturn(List.of("MAX(ts.demo.temperature)"));
        when(downsampledDataSet.getValues()).thenReturn(List.of(
            List.of(0L, 30.0d),
            List.of(2_000_000_000L, 50.0d)
        ));

        when(session.downsampleQuery(any(), anyLong(), anyLong(), eq(AggregateType.MAX), anyLong()))
            .thenReturn(downsampledDataSet);

        TimeSeriesQueryResultVO result = dataQueryService.queryTimeSeries(request);

        assertEquals(List.of(0L, 2000L), result.getTimestamps());
        assertEquals(1, result.getSeries().size());
        assertEquals("ts.demo.temperature", result.getSeries().get(0).getPath());
        assertEquals(List.of(30.0d, 50.0d), result.getSeries().get(0).getValues());
        verify(session).downsampleQuery(any(), anyLong(), anyLong(), eq(AggregateType.MAX), anyLong());
        verify(session, times(0)).queryData(any(), anyLong(), anyLong());
    }

    /**
     * 结构化表结构查询应返回列名与类型，不附带 _iginx_key。
     */
    @Test
    void queryStructuredSchema_shouldReturnColumnTypes() {
        Map<String, DataType> columnTypes = new LinkedHashMap<>();
        columnTypes.put("name", DataType.BINARY);
        columnTypes.put("age", DataType.INTEGER);
        when(structuredQueryHelper.loadColumnTypesByTablePath(anyString())).thenReturn(columnTypes);

        StructuredSchemaVO schema = dataQueryService.queryStructuredSchema("rt.user.*");

        assertEquals("rt.user", schema.getTablePath());
        assertEquals(2, schema.getColumns().size());
        assertEquals("name", schema.getColumns().get(0).getName());
        assertEquals("BINARY", schema.getColumns().get(0).getType());
        assertEquals("age", schema.getColumns().get(1).getName());
        assertEquals("INTEGER", schema.getColumns().get(1).getType());
    }

    /**
     * 任务结构化结果表应复用已有结构化表结构查询链路。
     */
    @Test
    void queryStructuredSchema_shouldAllowTaskResultTablePath() {
        Map<String, DataType> columnTypes = new LinkedHashMap<>();
        columnTypes.put("power", DataType.DOUBLE);
        when(structuredQueryHelper.loadColumnTypesByTablePath(anyString())).thenReturn(columnTypes);

        StructuredSchemaVO schema = dataQueryService.queryStructuredSchema("task.result.demoTask");

        assertEquals("task.result.demoTask", schema.getTablePath());
        assertEquals(1, schema.getColumns().size());
        assertEquals("power", schema.getColumns().get(0).getName());
        assertEquals("DOUBLE", schema.getColumns().get(0).getType());
    }

    /**
     * 结构化数据查询应走 SQL 路线，并正确生成分页 SQL 与 COUNT SQL。
     */
    @Test
    void queryStructured_shouldBuildSelectAndCountSql() throws Exception {
        StructuredQueryRequest request = new StructuredQueryRequest();
        request.setTablePath("rt.user");
        request.setPageNum(2);
        request.setPageSize(10);
        StructuredQueryCondition condition = new StructuredQueryCondition();
        condition.setField("age");
        condition.setOp("NOT IN");
        condition.setValue("18,19,20");
        request.setConditions(new ArrayList<>(List.of(condition)));

        Map<String, DataType> columnTypes = new LinkedHashMap<>();
        columnTypes.put("name", DataType.BINARY);
        columnTypes.put("age", DataType.INTEGER);
        when(structuredQueryHelper.loadColumnTypesByTablePath(anyString())).thenReturn(columnTypes);

        QueryDataSet dataSet = org.mockito.Mockito.mock(QueryDataSet.class);
        QueryDataSet countSet = org.mockito.Mockito.mock(QueryDataSet.class);
        when(structuredQueryHelper.executeQuery(anyString(), eq(10))).thenReturn(dataSet);
        when(structuredQueryHelper.executeQuery(anyString(), eq(1))).thenReturn(countSet);
        when(dataSet.getColumnList()).thenReturn(List.of("KEY", "name", "age"));
        when(structuredQueryHelper.readAll(eq(dataSet), anyList())).thenReturn(List.of(Map.of(
            "_iginx_key", 101L,
            "name", "Alice",
            "age", 20
        )));
        when(countSet.nextRow()).thenReturn(new Object[]{1L});

        StructuredQueryResultVO result = dataQueryService.queryStructured(request);

        assertIterableEquals(List.of("KEY", "name", "age"), result.getColumns());
        assertEquals(1, result.getPage().getTotal());
        assertEquals(2, result.getPage().getPageNum());
        assertEquals(10, result.getPage().getPageSize());
        assertEquals(1, result.getPage().getRecords().size());
        assertEquals("Alice", result.getPage().getRecords().get(0).get("name"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(structuredQueryHelper, times(2)).executeQuery(sqlCaptor.capture(), anyInt());
        List<String> sqlList = sqlCaptor.getAllValues();
        String dataSql = sqlList.stream().filter(sql -> sql.startsWith("SELECT *")).findFirst().orElse("");
        String countSql = sqlList.stream().filter(sql -> sql.startsWith("SELECT COUNT(*)")).findFirst().orElse("");

        assertTrue(dataSql.contains("FROM"), "数据 SQL 应包含 FROM 子句");
        assertTrue(dataSql.contains("user"), "数据 SQL 应包含表名");
        assertTrue(dataSql.contains("age NOT IN (18,19,20)"), "数据 SQL 应包含 NOT IN 条件");
        assertTrue(dataSql.contains("LIMIT 10 OFFSET 10"), "数据 SQL 应包含分页参数");
        assertTrue(countSql.contains("SELECT COUNT(*)"), "统计 SQL 应包含 COUNT(*)");
        assertTrue(countSql.contains("user"), "统计 SQL 应包含表名");
    }

    /**
     * 任务结构化结果表应支持分页查询。
     */
    @Test
    void queryStructured_shouldAllowTaskResultTablePath() throws Exception {
        StructuredQueryRequest request = new StructuredQueryRequest();
        request.setTablePath("task.result.demoTask");
        request.setPageNum(1);
        request.setPageSize(5);

        Map<String, DataType> columnTypes = new LinkedHashMap<>();
        columnTypes.put("power", DataType.DOUBLE);
        when(structuredQueryHelper.loadColumnTypesByTablePath(anyString())).thenReturn(columnTypes);

        QueryDataSet dataSet = org.mockito.Mockito.mock(QueryDataSet.class);
        QueryDataSet countSet = org.mockito.Mockito.mock(QueryDataSet.class);
        when(structuredQueryHelper.executeQuery(anyString(), eq(5))).thenReturn(dataSet);
        when(structuredQueryHelper.executeQuery(anyString(), eq(1))).thenReturn(countSet);
        when(dataSet.getColumnList()).thenReturn(List.of("KEY", "power"));
        when(structuredQueryHelper.readAll(eq(dataSet), anyList())).thenReturn(List.of(Map.of(
            "KEY", 0L,
            "power", 99.5d
        )));
        when(countSet.nextRow()).thenReturn(new Object[]{1L});

        StructuredQueryResultVO result = dataQueryService.queryStructured(request);

        assertEquals(1, result.getPage().getTotal());
        assertEquals(1, result.getPage().getRecords().size());
        assertEquals(99.5d, result.getPage().getRecords().get(0).get("power"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(structuredQueryHelper, times(2)).executeQuery(sqlCaptor.capture(), anyInt());
        List<String> sqlList = sqlCaptor.getAllValues();
        String dataSql = sqlList.stream().filter(sql -> sql.startsWith("SELECT *")).reduce((first, second) -> second).orElse("");
        assertTrue(dataSql.contains("task.result.demoTask"), "任务结果表查询 SQL 应包含 task.result.demoTask");
    }
}
