package com.xmu.iginx.assoc.modules.task.service.impl;

import cn.edu.tsinghua.iginx.session.QueryDataSet;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredQueryHelper;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionBinding;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionOutcome;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionPlan;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务模型执行引擎测试。
 */
@ExtendWith(MockitoExtension.class)
class TaskModelExecutionEngineTest {

    @Mock
    private IginxStorageWrapper iginxStorageWrapper;

    @Mock
    private IginxStructuredQueryHelper structuredQueryHelper;

    @Mock
    private TaskModelExecutor taskModelExecutor;

    /**
     * 纯 rt 结构化输入任务应整列读取数据，并将结果按 0 开始的 KEY 序列写回。
     */
    @Test
    void execute_shouldLoadStructuredRtInputsAsSeriesAndWriteZeroBasedKeys() throws Exception {
        TaskModelExecutionEngine engine = new TaskModelExecutionEngine(
            iginxStorageWrapper,
            structuredQueryHelper,
            List.of(taskModelExecutor)
        );

        QueryDataSet dataSet = org.mockito.Mockito.mock(QueryDataSet.class);
        when(structuredQueryHelper.executeQuery(any(String.class), eq(1000))).thenReturn(dataSet);
        when(dataSet.getColumnList()).thenReturn(List.of("temperature", "pressure"));
        when(dataSet.nextRow())
            .thenReturn(new Object[]{18.85d, 1.08d})
            .thenReturn(new Object[]{18.95d, 1.07d})
            .thenReturn(null);
        when(taskModelExecutor.supports("PY")).thenReturn(true);
        when(taskModelExecutor.execute(any(), any(), any()))
            .thenReturn(new TaskModelExecutor.ExecutionResult(List.of(5.399d, 5.4065d), ""));
        when(iginxStorageWrapper.executeSql(any(String.class))).thenReturn(null);

        TaskExecutionPlan plan = buildPlan();

        TaskExecutionOutcome outcome = engine.execute(plan, new byte[0]);

        ArgumentCaptor<LinkedHashMap<String, Object>> argumentCaptor = ArgumentCaptor.forClass(LinkedHashMap.class);
        verify(taskModelExecutor).execute(eq(plan), argumentCaptor.capture(), any());
        LinkedHashMap<String, Object> arguments = argumentCaptor.getValue();
        assertEquals(List.of(18.85d, 18.95d), arguments.get("temperature"));
        assertEquals(List.of(1.08d, 1.07d), arguments.get("pressure"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(iginxStorageWrapper).executeSql(sqlCaptor.capture());
        String insertSql = sqlCaptor.getValue();
        assertTrue(insertSql.contains("(0, 5.399"));
        assertTrue(insertSql.contains("(1, 5.4065"));

        ArgumentCaptor<String> structuredSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(structuredQueryHelper).executeQuery(structuredSqlCaptor.capture(), eq(1000));
        String selectSql = structuredSqlCaptor.getValue();
        assertTrue(selectSql.contains("SELECT temperature AS temperature, pressure AS pressure"));
        assertTrue(selectSql.contains("FROM rt.factory.boiler"));
        assertTrue(selectSql.contains("ORDER BY KEY ASC"));

        assertTrue(outcome.getExecLog().contains("结构化记录数量: 2"));
    }

    private TaskExecutionPlan buildPlan() {
        TaskExecutionBinding temperature = new TaskExecutionBinding();
        temperature.setName("temperature");
        temperature.setType("FLOAT");
        temperature.setDirection("INPUT");
        temperature.setConfiguredPath("rt.factory.boiler.temperature");
        temperature.setResolvedPath("rt.factory.boiler.temperature");
        temperature.setPathKind("RT");

        TaskExecutionBinding pressure = new TaskExecutionBinding();
        pressure.setName("pressure");
        pressure.setType("FLOAT");
        pressure.setDirection("INPUT");
        pressure.setConfiguredPath("rt.factory.boiler.pressure");
        pressure.setResolvedPath("rt.factory.boiler.pressure");
        pressure.setPathKind("RT");

        TaskExecutionBinding result = new TaskExecutionBinding();
        result.setName("result");
        result.setType("FLOAT");
        result.setDirection("OUTPUT");
        result.setConfiguredPath("");
        result.setResolvedPath("task.result.demo.result");
        result.setPathKind("TASK_RESULT");

        TaskExecutionSnapshot snapshot = new TaskExecutionSnapshot();
        snapshot.setFunctionName("predict_power");
        snapshot.setDefaultResultPrefix("task.result.demo");
        snapshot.setInputs(List.of(temperature, pressure));
        snapshot.setOutputs(List.of(result));

        TaskExecutionPlan plan = new TaskExecutionPlan();
        plan.setTaskId("demo-task");
        plan.setRuleName("demo-rule");
        plan.setModelType("PY");
        plan.setModelVersion("v1");
        plan.setSnapshot(snapshot);
        return plan;
    }
}
