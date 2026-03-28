package com.xmu.iginx.assoc.modules.task.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.modules.task.config.TaskExecutionProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MATLAB 模型执行器测试。
 */
class MatlabTaskModelExecutorTest {

    /**
     * 正常对象响应应能提取 result 字段。
     */
    @Test
    void readResponseValue_shouldReadObjectRoot(@TempDir Path tempDir) throws Exception {
        MatlabTaskModelExecutor executor = new MatlabTaskModelExecutor(new TaskExecutionProperties(), new ObjectMapper());
        Path responseFile = tempDir.resolve("response.json");
        Files.writeString(responseFile, "{\"result\":[1,2,3]}", StandardCharsets.UTF_8);

        Object value = executor.readResponseValue(responseFile);

        assertInstanceOf(List.class, value);
        assertEquals(List.of(1, 2, 3), value);
    }

    /**
     * 历史错误版本生成的数组根节点应兼容为结果序列。
     */
    @Test
    void readResponseValue_shouldUnwrapLegacyArrayRoot(@TempDir Path tempDir) throws Exception {
        MatlabTaskModelExecutor executor = new MatlabTaskModelExecutor(new TaskExecutionProperties(), new ObjectMapper());
        Path responseFile = tempDir.resolve("response.json");
        Files.writeString(
            responseFile,
            "[{\"result\":5.1},{\"result\":5.2},{\"result\":5.3}]",
            StandardCharsets.UTF_8
        );

        Object value = executor.readResponseValue(responseFile);

        assertInstanceOf(List.class, value);
        assertEquals(List.of(5.1, 5.2, 5.3), value);
    }

    /**
     * 构建运行器脚本时，应正确转义 MATLAB 注释中的 `%`，避免触发 Java 格式化异常。
     */
    @Test
    void buildRunnerScript_shouldEscapePercentComments(@TempDir Path tempDir) throws Exception {
        MatlabTaskModelExecutor executor = new MatlabTaskModelExecutor(new TaskExecutionProperties(), new ObjectMapper());
        Method method = MatlabTaskModelExecutor.class.getDeclaredMethod(
            "buildRunnerScript",
            Path.class,
            Path.class,
            Path.class
        );
        method.setAccessible(true);

        Object result = assertDoesNotThrow(() ->
            method.invoke(executor, tempDir.resolve("request.json"), tempDir.resolve("response.json"), tempDir)
        );

        assertInstanceOf(String.class, result);
        String script = (String) result;
        assertTrue(script.contains("% 这里必须先创建标量 struct，再用点赋值写入字段。"));
        assertTrue(script.contains("fprintf(fid, '%s', jsonencode(responseStruct));"));
    }
}
