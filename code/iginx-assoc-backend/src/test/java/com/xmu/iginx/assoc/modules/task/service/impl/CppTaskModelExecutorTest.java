package com.xmu.iginx.assoc.modules.task.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.modules.model.util.ModelFunctionSchemaParser;
import com.xmu.iginx.assoc.modules.model.util.ModelSchemaParser;
import com.xmu.iginx.assoc.modules.task.config.TaskExecutionProperties;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionPlan;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C++ 模型执行器测试。
 */
class CppTaskModelExecutorTest {

    /**
     * 标量输入的 C++ 函数应能编译执行并返回数值结果。
     */
    @Test
    void execute_shouldRunScalarCppFunction(@TempDir Path tempDir) throws Exception {
        CppTaskModelExecutor executor = buildExecutor(tempDir);
        String script = """
            double predict_power(double temperature, double pressure) {
                return temperature * 0.2 + pressure;
            }
            """;
        LinkedHashMap<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("temperature", 18.5d);
        arguments.put("pressure", 1.2d);

        TaskModelExecutor.ExecutionResult result = executor.execute(
            buildPlan("task-scalar", "demo.cpp", "predict_power"),
            arguments,
            script.getBytes(StandardCharsets.UTF_8)
        );

        assertInstanceOf(Double.class, result.rawResult());
        assertEquals(4.9d, ((Number) result.rawResult()).doubleValue(), 1e-9);
    }

    /**
     * 序列输入的标量 C++ 函数应逐点调用并聚合 pair 输出。
     */
    @Test
    void execute_shouldAggregatePointwisePairOutputs(@TempDir Path tempDir) throws Exception {
        CppTaskModelExecutor executor = buildExecutor(tempDir);
        String script = """
            #include <utility>

            std::pair<double, bool> classify(double temperature) {
                return { temperature * 0.5, temperature > 20.0 };
            }
            """;
        LinkedHashMap<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("temperature", List.of(18.0d, 24.0d));

        TaskModelExecutor.ExecutionResult result = executor.execute(
            buildPlan("task-sequence", "classify.cpp", "classify"),
            arguments,
            script.getBytes(StandardCharsets.UTF_8)
        );

        assertInstanceOf(List.class, result.rawResult());
        @SuppressWarnings("unchecked")
        List<Object> outputs = (List<Object>) result.rawResult();
        assertEquals(2, outputs.size());
        assertInstanceOf(List.class, outputs.get(0));
        @SuppressWarnings("unchecked")
        List<Object> scores = (List<Object>) outputs.get(0);
        assertEquals(2, scores.size());
        assertEquals(9.0d, ((Number) scores.get(0)).doubleValue(), 1e-9);
        assertEquals(12.0d, ((Number) scores.get(1)).doubleValue(), 1e-9);
        assertEquals(List.of(false, true), outputs.get(1));
        assertTrue(String.valueOf(result.runtimeLog()).contains("运行日志")
            || String.valueOf(result.runtimeLog()).isBlank());
    }

    /**
     * 构造测试执行器。
     */
    private CppTaskModelExecutor buildExecutor(Path tempDir) {
        TaskExecutionProperties properties = new TaskExecutionProperties();
        properties.setCppCompilerExecutable("g++");
        properties.setWorkDir(tempDir.toString());
        ModelFunctionSchemaParser parser = new ModelFunctionSchemaParser(new ModelSchemaParser());
        return new CppTaskModelExecutor(properties, new ObjectMapper(), parser);
    }

    /**
     * 构造最小可执行计划。
     */
    private TaskExecutionPlan buildPlan(String taskId, String fileName, String functionName) {
        TaskExecutionSnapshot snapshot = new TaskExecutionSnapshot();
        snapshot.setFunctionName(functionName);

        TaskExecutionPlan plan = new TaskExecutionPlan();
        plan.setTaskId(taskId);
        plan.setModelFileName(fileName);
        plan.setSnapshot(snapshot);
        return plan;
    }
}
