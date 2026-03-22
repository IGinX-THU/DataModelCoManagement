package com.xmu.iginx.assoc.modules.data.service.impl;

import cn.edu.tsinghua.iginx.session.QueryDataSet;
import cn.edu.tsinghua.iginx.session.Session;
import cn.edu.tsinghua.iginx.session.SessionQueryDataSet;
import cn.edu.tsinghua.iginx.thrift.DataType;
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
     * 结构化表结构查询应返回列名与类型，不附带 _iginx_key。
     */
    @Test
    void queryStructuredSchema_shouldReturnColumnTypes() {
        Map<String, DataType> columnTypes = new LinkedHashMap<>();
        columnTypes.put("name", DataType.BINARY);
        columnTypes.put("age", DataType.INTEGER);
        when(structuredQueryHelper.loadColumnTypesByTablePath(anyString())).thenReturn(columnTypes);

        StructuredSchemaVO schema = dataQueryService.queryStructuredSchema("root.rt.user.*");

        assertEquals("rt.user", schema.getTablePath());
        assertEquals(2, schema.getColumns().size());
        assertEquals("name", schema.getColumns().get(0).getName());
        assertEquals("BINARY", schema.getColumns().get(0).getType());
        assertEquals("age", schema.getColumns().get(1).getName());
        assertEquals("INTEGER", schema.getColumns().get(1).getType());
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

        assertIterableEquals(List.of("_iginx_key", "name", "age"), result.getColumns());
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

        assertTrue(dataSql.contains("FROM rt.user"), "数据 SQL 应包含表路径");
        assertTrue(dataSql.contains("age NOT IN (18,19,20)"), "数据 SQL 应包含 NOT IN 条件");
        assertTrue(dataSql.contains("LIMIT 10 OFFSET 10"), "数据 SQL 应包含分页参数");
        assertTrue(countSql.contains("SELECT COUNT(*) FROM rt.user"), "统计 SQL 应包含 COUNT(*)");
    }
}
