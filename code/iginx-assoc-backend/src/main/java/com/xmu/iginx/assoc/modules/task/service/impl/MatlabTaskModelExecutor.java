package com.xmu.iginx.assoc.modules.task.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.task.config.TaskExecutionProperties;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionPlan;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MATLAB 模型执行器。
 * <p>
 * 执行策略：
 * 1. 将模型脚本文本写成临时 `.m` 文件；
 * 2. 通过 `matlab -batch` 调起运行器脚本；
 * 3. 由运行器脚本完成 `jsondecode -> feval -> jsonencode`；
 * 4. Java 侧接收原始返回值，并继续做严格输出校验与 IGinX 回写。
 * </p>
 */
@Component
public class MatlabTaskModelExecutor extends AbstractCommandTaskModelExecutor {

    private static final String RUNNER_FILE = "task_matlab_runner.m";

    private final ObjectMapper objectMapper;

    public MatlabTaskModelExecutor(TaskExecutionProperties taskExecutionProperties, ObjectMapper objectMapper) {
        super(taskExecutionProperties);
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String modelType) {
        return "MAT".equalsIgnoreCase(modelType) || "MATLAB".equalsIgnoreCase(modelType);
    }

    @Override
    public ExecutionResult execute(TaskExecutionPlan plan,
                                   LinkedHashMap<String, Object> arguments,
                                   byte[] modelBytes) throws Exception {
        Path workDir = createTaskWorkDir(plan.getTaskId());
        Path modelFile = workDir.resolve(resolveMatlabFileName(plan.getSnapshot().getFunctionName()));
        Path requestFile = workDir.resolve("request-" + UUID.randomUUID() + ".json");
        Path responseFile = workDir.resolve("response-" + UUID.randomUUID() + ".json");
        Path runnerFile = workDir.resolve(RUNNER_FILE);

        writeBytes(modelFile, modelBytes);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("function_name", plan.getSnapshot().getFunctionName());
        request.put("inputs", arguments);
        request.put("input_order", plan.getSnapshot().getInputs() == null
            ? List.of()
            : plan.getSnapshot().getInputs().stream().map(item -> item.getName()).toList());
        request.put("output_count", plan.getSnapshot().getOutputs() == null ? 0 : plan.getSnapshot().getOutputs().size());
        writeUtf8(requestFile, objectMapper.writeValueAsString(request));
        writeUtf8(runnerFile, buildRunnerScript(requestFile, responseFile, workDir));

        List<String> command = List.of(
            super.taskExecutionProperties.getMatlabExecutable(),
            "-batch",
            "run('" + toMatlabPath(runnerFile) + "')"
        );
        String runtimeLog = runCommand(command, workDir);
        if (!Files.exists(responseFile)) {
            throw BizException.badRequest("MATLAB 模型未返回有效结果");
        }
        return new ExecutionResult(readResponseValue(responseFile), runtimeLog);
    }

    /**
     * MATLAB 临时文件名必须与主函数名保持一致。
     */
    private String resolveMatlabFileName(String functionName) {
        return functionName + ".m";
    }

    /**
     * 构建 MATLAB 运行器脚本。
     */
    private String buildRunnerScript(Path requestFile, Path responseFile, Path workDir) {
        return """
            requestPath = '%s';
            responsePath = '%s';
            addpath('%s');
            payload = jsondecode(fileread(requestPath));
            functionName = char(string(payload.function_name));
            args = {};
            if isfield(payload, 'inputs') && ~isempty(payload.inputs)
                inputStruct = payload.inputs;
                inputNames = cellstr(string(payload.input_order));
                args = cell(1, numel(inputNames));
                for idx = 1:numel(inputNames)
                    args{idx} = inputStruct.(inputNames{idx});
                end
            end
            outputCount = double(payload.output_count);
            if outputCount <= 0
                error('MATLAB runner 输出参数数量不合法');
            end
            func = str2func(functionName);
            resultValue = invokeWithFallback(func, args, outputCount);
            %% 这里必须先创建标量 struct，再用点赋值写入字段。
            %% 如果直接使用 struct('result', cellArray)，MATLAB 会生成 struct 数组，
            %% jsonencode 后根节点会变成 JSON 数组，导致 Java 侧无法按对象结构读取。
            responseStruct = struct();
            responseStruct.result = resultValue;
            fid = fopen(responsePath, 'w', 'n', 'UTF-8');
            if fid < 0
                error('无法写入 MATLAB 响应文件');
            end
            cleaner = onCleanup(@() fclose(fid));
            fprintf(fid, '%%s', jsonencode(responseStruct));
            
            function value = normalizeValue(raw)
                if iscell(raw)
                    value = cell(size(raw));
                    for innerIdx = 1:numel(raw)
                        value{innerIdx} = normalizeValue(raw{innerIdx});
                    end
                    return;
                end
                if isstruct(raw)
                    names = fieldnames(raw);
                    value = struct();
                    for innerIdx = 1:numel(names)
                        key = names{innerIdx};
                        value.(key) = normalizeValue(raw.(key));
                    end
                    return;
                end
                if isstring(raw)
                    if isscalar(raw)
                        value = char(raw);
                    else
                        value = cellstr(raw);
                    end
                    return;
                end
                if ischar(raw) || isnumeric(raw) || islogical(raw)
                    value = raw;
                    return;
                end
                if istable(raw)
                    value = normalizeValue(table2struct(raw));
                    return;
                end
                value = char(string(raw));
            end

            function resultValue = invokeWithFallback(func, args, outputCount)
                try
                    directResults = cell(1, outputCount);
                    [directResults{:}] = func(args{:});
                    if outputCount == 1
                        resultValue = normalizeValue(directResults{1});
                    else
                        resultValue = normalizeValue(directResults);
                    end
                    return;
                catch directError
                    pointwiseLength = resolvePointwiseLength(args);
                    if pointwiseLength <= 0
                        rethrow(directError);
                    end
                    aggregated = cell(1, outputCount);
                    for outputIdx = 1:outputCount
                        aggregated{outputIdx} = cell(1, pointwiseLength);
                    end
                    for pointIdx = 1:pointwiseLength
                        currentArgs = cell(1, numel(args));
                        for argIdx = 1:numel(args)
                            currentArgs{argIdx} = sliceValue(args{argIdx}, pointIdx);
                        end
                        currentResults = cell(1, outputCount);
                        [currentResults{:}] = func(currentArgs{:});
                        for outputIdx = 1:outputCount
                            aggregated{outputIdx}{pointIdx} = normalizeValue(currentResults{outputIdx});
                        end
                    end
                    if outputCount == 1
                        resultValue = aggregated{1};
                    else
                        resultValue = aggregated;
                    end
                end
            end

            function lengthValue = resolvePointwiseLength(args)
                lengthValue = 0;
                for argIdx = 1:numel(args)
                    current = args{argIdx};
                    if isPointwiseSequence(current)
                        currentLength = numel(current);
                        if lengthValue == 0
                            lengthValue = currentLength;
                        elseif lengthValue ~= currentLength
                            error('时序输入长度不一致，无法逐点调用 MATLAB 模型');
                        end
                    end
                end
            end

            function flag = isPointwiseSequence(value)
                if ischar(value)
                    flag = false;
                    return;
                end
                if isstring(value)
                    flag = numel(value) > 1;
                    return;
                end
                if isnumeric(value) || islogical(value) || iscell(value)
                    flag = numel(value) > 1;
                    return;
                end
                flag = false;
            end

            function sliced = sliceValue(value, index)
                if ischar(value)
                    sliced = value;
                    return;
                end
                if isstring(value)
                    if numel(value) > 1
                        sliced = value(index);
                    else
                        sliced = value;
                    end
                    return;
                end
                if iscell(value)
                    if numel(value) > 1
                        sliced = value{index};
                    else
                        sliced = value{1};
                    end
                    return;
                end
                if isnumeric(value) || islogical(value)
                    if numel(value) > 1
                        sliced = value(index);
                    else
                        sliced = value;
                    end
                    return;
                end
                sliced = value;
            end
            """.formatted(
            escapeMatlabLiteral(toMatlabPath(requestFile)),
            escapeMatlabLiteral(toMatlabPath(responseFile)),
            escapeMatlabLiteral(toMatlabPath(workDir))
        );
    }

    /**
     * 将 Windows 路径转成 MATLAB 更稳定的正斜杠写法。
     */
    private String toMatlabPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("\\", "/");
    }

    /**
     * 转义 MATLAB 单引号字符串。
     */
    private String escapeMatlabLiteral(String value) {
        return value.replace("'", "''");
    }

    /**
     * 读取 MATLAB 响应文件。
     * <p>
     * 正常情况下，运行器会写出：
     * {"result": ...}
     * </p>
     * <p>
     * 为兼容历史错误版本，这里也接受数组根节点：
     * [{"result": 1}, {"result": 2}]
     * 并自动折叠为 [1, 2]。
     * </p>
     */
    Object readResponseValue(Path responseFile) throws Exception {
        JsonNode root = objectMapper.readTree(responseFile.toFile());
        if (root == null || root.isNull()) {
            throw BizException.badRequest("MATLAB 响应文件为空");
        }
        if (root.isObject()) {
            JsonNode resultNode = root.get("result");
            if (resultNode == null || resultNode.isMissingNode()) {
                throw BizException.badRequest("MATLAB 响应缺少 result 字段");
            }
            return objectMapper.convertValue(resultNode, Object.class);
        }
        if (root.isArray()) {
            return unwrapLegacyArrayRoot(root);
        }
        return objectMapper.convertValue(root, Object.class);
    }

    /**
     * 兼容历史错误版本的数组根节点响应。
     */
    private Object unwrapLegacyArrayRoot(JsonNode root) {
        List<Object> values = new ArrayList<>();
        for (JsonNode item : root) {
            if (item != null && item.isObject() && item.size() == 1 && item.has("result")) {
                values.add(objectMapper.convertValue(item.get("result"), Object.class));
                continue;
            }
            values.add(objectMapper.convertValue(item, Object.class));
        }
        return values;
    }
}
