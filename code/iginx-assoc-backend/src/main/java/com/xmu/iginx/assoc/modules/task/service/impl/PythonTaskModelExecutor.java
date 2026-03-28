package com.xmu.iginx.assoc.modules.task.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.task.config.TaskExecutionProperties;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Python 模型执行器。
 * <p>
 * 执行策略：
 * 1. 将 IGinX 中提取好的输入参数写入 JSON；
 * 2. 将模型脚本落到任务临时目录；
 * 3. 通过 importlib 动态加载脚本文件，并调用规则指定函数；
 * 4. 将函数返回结果序列化为 JSON，再交回 Java 侧做严格输出校验与回写。
 * </p>
 */
@Slf4j
@Component
public class PythonTaskModelExecutor extends AbstractCommandTaskModelExecutor {

    private static final String RUNNER_FILE = "task_python_runner.py";

    private final ObjectMapper objectMapper;

    public PythonTaskModelExecutor(TaskExecutionProperties taskExecutionProperties, ObjectMapper objectMapper) {
        super(taskExecutionProperties);
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String modelType) {
        return "PY".equalsIgnoreCase(modelType) || "PYTHON".equalsIgnoreCase(modelType);
    }

    @Override
    public ExecutionResult execute(TaskExecutionPlan plan,
                                   LinkedHashMap<String, Object> arguments,
                                   byte[] modelBytes) throws Exception {
        Path workDir = createTaskWorkDir(plan.getTaskId());
        String fileName = resolvePythonFileName(plan.getModelFileName(), plan.getSnapshot().getFunctionName());
        Path modelFile = workDir.resolve(fileName);
        Path requestFile = workDir.resolve("request-" + UUID.randomUUID() + ".json");
        Path responseFile = workDir.resolve("response-" + UUID.randomUUID() + ".json");
        Path runnerFile = workDir.resolve(RUNNER_FILE);

        writeBytes(modelFile, modelBytes);
        writeUtf8(runnerFile, buildRunnerScript());

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model_file", modelFile.toString());
        request.put("function_name", plan.getSnapshot().getFunctionName());
        request.put("inputs", arguments);
        writeUtf8(requestFile, objectMapper.writeValueAsString(request));

        List<String> command = List.of(
            super.taskExecutionProperties.getPythonExecutable(),
            "-X",
            "utf8",
            runnerFile.toString(),
            requestFile.toString(),
            responseFile.toString()
        );
        String runtimeLog = runCommand(command, workDir);
        if (!java.nio.file.Files.exists(responseFile)) {
            throw BizException.badRequest("Python 模型未返回有效结果");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(responseFile.toFile(), Map.class);
        return new ExecutionResult(response.get("result"), runtimeLog);
    }

    /**
     * 解析 Python 临时脚本文件名。
     */
    private String resolvePythonFileName(String originalFileName, String functionName) {
        String base = originalFileName == null ? "" : originalFileName.trim();
        if (base.endsWith(".py")) {
            return base;
        }
        if (!base.isBlank()) {
            return base + ".py";
        }
        return functionName + ".py";
    }

    /**
     * 构建 Python 运行器脚本。
     */
    private String buildRunnerScript() {
        return """
            import importlib.util
            import json
            import os
            import sys
            import traceback
            
            def to_jsonable(value):
                if hasattr(value, 'item') and callable(getattr(value, 'item')):
                    try:
                        return to_jsonable(value.item())
                    except Exception:
                        pass
                if hasattr(value, 'tolist') and callable(getattr(value, 'tolist')):
                    try:
                        return to_jsonable(value.tolist())
                    except Exception:
                        pass
                if isinstance(value, dict):
                    return {str(k): to_jsonable(v) for k, v in value.items()}
                if isinstance(value, (list, tuple, set)):
                    return [to_jsonable(item) for item in value]
                if isinstance(value, bytes):
                    return value.decode('utf-8', errors='replace')
                return value

            def is_sequence_input(value):
                return isinstance(value, list)

            def resolve_pointwise_length(inputs):
                lengths = []
                for value in inputs.values():
                    if is_sequence_input(value):
                        lengths.append(len(value))
                if not lengths:
                    return 0
                unique_lengths = set(lengths)
                if len(unique_lengths) != 1:
                    raise RuntimeError('时序输入长度不一致，无法逐点调用模型函数')
                return lengths[0]

            def aggregate_pointwise_results(results):
                if not results:
                    return []
                first = results[0]
                if isinstance(first, dict):
                    keys = list(first.keys())
                    return {
                        str(key): [to_jsonable(item.get(key)) for item in results]
                        for key in keys
                    }
                if isinstance(first, (list, tuple)):
                    width = len(first)
                    aggregated = []
                    for idx in range(width):
                        aggregated.append([to_jsonable(item[idx]) for item in results])
                    return aggregated
                return [to_jsonable(item) for item in results]

            def invoke_with_fallback(func, inputs):
                try:
                    return func(**inputs)
                except TypeError:
                    pointwise_length = resolve_pointwise_length(inputs)
                    if pointwise_length <= 0:
                        raise
                    results = []
                    for idx in range(pointwise_length):
                        current = {
                            key: (value[idx] if is_sequence_input(value) else value)
                            for key, value in inputs.items()
                        }
                        results.append(func(**current))
                    return aggregate_pointwise_results(results)
            
            def main():
                if len(sys.argv) < 3:
                    raise RuntimeError('Python runner 参数不足')
                request_path = sys.argv[1]
                response_path = sys.argv[2]
                with open(request_path, 'r', encoding='utf-8') as file:
                    payload = json.load(file)
                model_file = payload['model_file']
                function_name = payload['function_name']
                inputs = payload.get('inputs') or {}
                sys.path.insert(0, os.path.dirname(model_file))
                spec = importlib.util.spec_from_file_location('assoc_task_model', model_file)
                if spec is None or spec.loader is None:
                    raise RuntimeError(f'无法加载 Python 模型文件: {model_file}')
                module = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(module)
                func = getattr(module, function_name, None)
                if not callable(func):
                    raise RuntimeError(f'未找到可调用函数: {function_name}')
                result = invoke_with_fallback(func, inputs)
                with open(response_path, 'w', encoding='utf-8') as file:
                    json.dump({'result': to_jsonable(result)}, file, ensure_ascii=False)
            
            if __name__ == '__main__':
                try:
                    main()
                except Exception:
                    traceback.print_exc()
                    raise
            """;
    }
}
