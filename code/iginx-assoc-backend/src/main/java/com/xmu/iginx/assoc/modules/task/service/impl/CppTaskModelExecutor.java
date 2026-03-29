package com.xmu.iginx.assoc.modules.task.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.model.util.CppFunctionSupport;
import com.xmu.iginx.assoc.modules.model.util.ModelFunctionSchemaParser;
import com.xmu.iginx.assoc.modules.task.config.TaskExecutionProperties;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionPlan;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * C++ 模型执行器。
 * <p>
 * 执行策略：
 * 1. 解析目标函数的参数类型；
 * 2. 生成任务专属的 C++ 包装代码；
 * 3. 使用 g++ 编译并执行包装程序；
 * 4. 将返回结果写成 JSON，再交回 Java 侧做统一校验与回写。
 * </p>
 */
@Component
public class CppTaskModelExecutor extends AbstractCommandTaskModelExecutor {

    private static final String RUNNER_SOURCE_FILE = "task_cpp_runner.cpp";
    private static final String INPUT_DIR_NAME = "cpp-inputs";
    private static final String RESPONSE_FILE_NAME = "response.json";

    private final ObjectMapper objectMapper;
    private final ModelFunctionSchemaParser functionSchemaParser;

    public CppTaskModelExecutor(TaskExecutionProperties taskExecutionProperties,
                                ObjectMapper objectMapper,
                                ModelFunctionSchemaParser functionSchemaParser) {
        super(taskExecutionProperties);
        this.objectMapper = objectMapper;
        this.functionSchemaParser = functionSchemaParser;
    }

    @Override
    public boolean supports(String modelType) {
        return "CPP".equalsIgnoreCase(modelType) || "C++".equalsIgnoreCase(modelType);
    }

    @Override
    public ExecutionResult execute(TaskExecutionPlan plan,
                                   LinkedHashMap<String, Object> arguments,
                                   byte[] modelBytes) throws Exception {
        CppFunctionSupport.CppFunctionDescriptor function = functionSchemaParser.resolveCppFunctionDescriptor(
            modelBytes,
            plan.getSnapshot().getFunctionName()
        );
        validateInputs(function, arguments);
        boolean pointwiseMode = shouldUsePointwiseMode(function, arguments);

        Path workDir = createTaskWorkDir(plan.getTaskId());
        Path inputDir = workDir.resolve(INPUT_DIR_NAME);
        Files.createDirectories(inputDir);

        String modelFileName = resolveCppFileName(plan.getModelFileName(), function.name());
        Path modelFile = workDir.resolve(modelFileName);
        Path runnerSource = workDir.resolve(RUNNER_SOURCE_FILE);
        Path responseFile = workDir.resolve(RESPONSE_FILE_NAME);
        String runnerBinaryName = resolveRunnerBinaryName();
        Path runnerBinary = workDir.resolve(runnerBinaryName);

        writeBytes(modelFile, modelBytes);
        writeInputFiles(inputDir, function, arguments);
        writeUtf8(runnerSource, buildRunnerSource(modelFileName, function, arguments, pointwiseMode));

        String compileLog = runCommand(buildCompileCommand(runnerSource.getFileName().toString(), runnerBinaryName), workDir);
        if (!Files.exists(runnerBinary)) {
            throw BizException.badRequest("C++ 包装程序编译完成后未生成可执行文件");
        }
        String runtimeLog = runCommand(buildRunCommand(runnerBinary), workDir);
        if (!Files.exists(responseFile)) {
            throw BizException.badRequest("C++ 模型未返回有效结果");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(responseFile.toFile(), Map.class);
        return new ExecutionResult(response.get("result"), mergeLogs(compileLog, runtimeLog));
    }

    /**
     * 校验输入参数与函数签名。
     */
    private void validateInputs(CppFunctionSupport.CppFunctionDescriptor function,
                                LinkedHashMap<String, Object> arguments) {
        if (function.parameters().size() != arguments.size()) {
            throw BizException.badRequest("C++ 模型输入数量与函数签名不一致");
        }
        for (CppFunctionSupport.CppParameterDescriptor parameter : function.parameters()) {
            if (!arguments.containsKey(parameter.name())) {
                throw BizException.badRequest("C++ 模型缺少输入参数: " + parameter.name());
            }
            if (!parameter.executionSupported()) {
                throw BizException.badRequest("C++ 参数类型暂不支持自动执行: " + parameter.rawType());
            }
            if (containsNull(arguments.get(parameter.name()))) {
                throw BizException.badRequest("C++ 输入参数[" + parameter.name() + "] 包含空值，当前暂不支持执行");
            }
        }
    }

    /**
     * 判断是否需要逐点执行。
     */
    private boolean shouldUsePointwiseMode(CppFunctionSupport.CppFunctionDescriptor function,
                                           LinkedHashMap<String, Object> arguments) {
        boolean hasScalarSequence = false;
        boolean hasVectorSequence = false;
        for (CppFunctionSupport.CppParameterDescriptor parameter : function.parameters()) {
            Object value = arguments.get(parameter.name());
            if (!(value instanceof List<?>)) {
                continue;
            }
            if (parameter.sequenceType()) {
                hasVectorSequence = true;
            } else {
                hasScalarSequence = true;
            }
        }
        if (hasScalarSequence && hasVectorSequence) {
            throw BizException.badRequest("C++ 模型暂不支持同时混用逐点标量参数与 vector 参数");
        }
        return hasScalarSequence;
    }

    /**
     * 将任务输入写入临时文件。
     */
    private void writeInputFiles(Path inputDir,
                                 CppFunctionSupport.CppFunctionDescriptor function,
                                 LinkedHashMap<String, Object> arguments) {
        for (CppFunctionSupport.CppParameterDescriptor parameter : function.parameters()) {
            Path file = inputDir.resolve(parameter.name() + ".txt");
            Object value = arguments.get(parameter.name());
            if (value instanceof List<?> list) {
                List<String> lines = new ArrayList<>();
                for (Object item : list) {
                    lines.add(serializeInputValue(item));
                }
                writeUtf8(file, String.join(System.lineSeparator(), lines));
            } else {
                writeUtf8(file, serializeInputValue(value));
            }
        }
    }

    /**
     * 序列化输入值。
     */
    private String serializeInputValue(Object value) {
        if (value == null) {
            throw BizException.badRequest("C++ 模型不支持空值输入");
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (value instanceof Boolean bool) {
            return bool ? "true" : "false";
        }
        return String.valueOf(value);
    }

    /**
     * 是否包含空值。
     */
    private boolean containsNull(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof List<?> list) {
            return list.stream().anyMatch(this::containsNull);
        }
        return false;
    }

    /**
     * 生成 C++ 包装代码。
     */
    private String buildRunnerSource(String modelFileName,
                                     CppFunctionSupport.CppFunctionDescriptor function,
                                     LinkedHashMap<String, Object> arguments,
                                     boolean pointwiseMode) {
        StringBuilder builder = new StringBuilder();
        builder.append("""
            #include <cctype>
            #include <fstream>
            #include <iomanip>
            #include <iostream>
            #include <sstream>
            #include <stdexcept>
            #include <string>
            #include <tuple>
            #include <type_traits>
            #include <utility>
            #include <vector>
            #include """
        ).append('"').append(escapeCppString(modelFileName)).append('"').append("\n\n");

        builder.append("""
            template<typename T>
            inline constexpr bool dependent_false_v = false;

            std::string trim_copy(const std::string& value) {
                std::size_t begin = 0;
                while (begin < value.size() && std::isspace(static_cast<unsigned char>(value[begin]))) {
                    begin++;
                }
                std::size_t end = value.size();
                while (end > begin && std::isspace(static_cast<unsigned char>(value[end - 1]))) {
                    end--;
                }
                return value.substr(begin, end - begin);
            }

            std::string read_text_file(const std::string& path) {
                std::ifstream input(path, std::ios::binary);
                if (!input.is_open()) {
                    throw std::runtime_error("无法读取 C++ 输入文件: " + path);
                }
                std::ostringstream buffer;
                buffer << input.rdbuf();
                return buffer.str();
            }

            std::vector<std::string> read_lines_file(const std::string& path) {
                std::ifstream input(path, std::ios::binary);
                if (!input.is_open()) {
                    throw std::runtime_error("无法读取 C++ 输入文件: " + path);
                }
                std::vector<std::string> lines;
                std::string line;
                while (std::getline(input, line)) {
                    if (!line.empty() && line.back() == '\\r') {
                        line.pop_back();
                    }
                    lines.push_back(line);
                }
                return lines;
            }

            template<typename T>
            T parse_scalar(const std::string& text) {
                if constexpr (std::is_same_v<T, std::string>) {
                    return text;
                } else {
                    std::string value = trim_copy(text);
                    if constexpr (std::is_same_v<T, bool>) {
                        std::string lower;
                        lower.reserve(value.size());
                        for (char ch : value) {
                            lower.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(ch))));
                        }
                        if (lower == "true" || lower == "1") {
                            return true;
                        }
                        if (lower == "false" || lower == "0") {
                            return false;
                        }
                        throw std::runtime_error("无法将输入值转换为 bool: " + value);
                    } else if constexpr (std::is_integral_v<T> && !std::is_same_v<T, bool>) {
                        if constexpr (std::is_signed_v<T>) {
                            return static_cast<T>(std::stoll(value));
                        } else {
                            return static_cast<T>(std::stoull(value));
                        }
                    } else if constexpr (std::is_floating_point_v<T>) {
                        return static_cast<T>(std::stold(value));
                    } else {
                        static_assert(dependent_false_v<T>, "不支持的 C++ 输入参数类型");
                    }
                }
            }

            template<typename T>
            T load_scalar(const std::string& path) {
                return parse_scalar<T>(read_text_file(path));
            }

            template<typename T>
            std::vector<T> load_sequence(const std::string& path) {
                auto lines = read_lines_file(path);
                std::vector<T> values;
                values.reserve(lines.size());
                for (const auto& line : lines) {
                    values.push_back(parse_scalar<T>(line));
                }
                return values;
            }

            std::string escape_json(const std::string& value) {
                std::ostringstream output;
                for (char ch : value) {
                    switch (ch) {
                        case '\\\\': output << "\\\\"; break;
                        case '\"': output << "\\\""; break;
                        case '\\n': output << "\\\\n"; break;
                        case '\\r': output << "\\\\r"; break;
                        case '\\t': output << "\\\\t"; break;
                        default: output << ch; break;
                    }
                }
                return output.str();
            }

            std::string join_json_parts(const std::vector<std::string>& parts) {
                std::ostringstream output;
                for (std::size_t index = 0; index < parts.size(); ++index) {
                    if (index > 0) {
                        output << ",";
                    }
                    output << parts[index];
                }
                return output.str();
            }

            template<typename T>
            struct is_vector : std::false_type {};

            template<typename T, typename Allocator>
            struct is_vector<std::vector<T, Allocator>> : std::true_type {};

            template<typename T>
            struct is_pair : std::false_type {};

            template<typename Left, typename Right>
            struct is_pair<std::pair<Left, Right>> : std::true_type {};

            template<typename T>
            struct is_tuple : std::false_type {};

            template<typename... Args>
            struct is_tuple<std::tuple<Args...>> : std::true_type {};

            template<typename T>
            std::string to_json(const T& value);

            template<typename Tuple, std::size_t... Indexes>
            std::string tuple_to_json_impl(const Tuple& value, std::index_sequence<Indexes...>) {
                std::vector<std::string> parts = { };
                parts.reserve(sizeof...(Indexes));
                (parts.push_back([&]() { return to_json(std::get<Indexes>(value)); }()), ...);
                return "[" + join_json_parts(parts) + "]";
            }

            template<typename... Args>
            std::string to_json(const std::tuple<Args...>& value) {
                return tuple_to_json_impl(value, std::index_sequence_for<Args...>{});
            }

            template<typename T>
            std::string to_json(const T& value) {
                using Decayed = std::decay_t<T>;
                if constexpr (std::is_same_v<Decayed, std::string>) {
                    return "\"" + escape_json(value) + "\"";
                } else if constexpr (std::is_same_v<Decayed, bool>) {
                    return value ? "true" : "false";
                } else if constexpr (std::is_integral_v<Decayed> && !std::is_same_v<Decayed, bool>) {
                    return std::to_string(value);
                } else if constexpr (std::is_floating_point_v<Decayed>) {
                    std::ostringstream output;
                    output << std::setprecision(17) << value;
                    return output.str();
                } else if constexpr (is_vector<Decayed>::value) {
                    std::vector<std::string> parts;
                    parts.reserve(value.size());
                    for (const auto& item : value) {
                        parts.push_back(to_json(item));
                    }
                    return "[" + join_json_parts(parts) + "]";
                } else if constexpr (is_pair<Decayed>::value) {
                    return "[" + to_json(value.first) + "," + to_json(value.second) + "]";
                } else if constexpr (is_tuple<Decayed>::value) {
                    return tuple_to_json(value);
                } else {
                    std::ostringstream output;
                    output << value;
                    return "\"" + escape_json(output.str()) + "\"";
                }
            }

            template<typename Tuple, std::size_t... Indexes>
            auto make_tuple_of_vectors_impl(std::index_sequence<Indexes...>) {
                return std::tuple<std::vector<std::tuple_element_t<Indexes, Tuple>>...>{};
            }

            template<typename Tuple>
            auto make_tuple_of_vectors() {
                return make_tuple_of_vectors_impl<Tuple>(std::make_index_sequence<std::tuple_size_v<Tuple>>{});
            }

            template<typename TupleVectors, typename TupleValue, std::size_t... Indexes>
            void append_tuple_result_impl(TupleVectors& outputs,
                                          const TupleValue& value,
                                          std::index_sequence<Indexes...>) {
                (std::get<Indexes>(outputs).push_back(std::get<Indexes>(value)), ...);
            }

            template<typename TupleVectors, typename TupleValue>
            void append_tuple_result(TupleVectors& outputs, const TupleValue& value) {
                append_tuple_result_impl(
                    outputs,
                    value,
                    std::make_index_sequence<std::tuple_size_v<std::decay_t<TupleValue>>>{}
                );
            }

            template<typename TupleVectors, std::size_t... Indexes>
            std::string tuple_vectors_to_json_impl(const TupleVectors& outputs,
                                                   std::index_sequence<Indexes...>) {
                std::vector<std::string> parts = { };
                parts.reserve(sizeof...(Indexes));
                (parts.push_back([&]() { return to_json(std::get<Indexes>(outputs)); }()), ...);
                return "[" + join_json_parts(parts) + "]";
            }

            template<typename TupleVectors>
            std::string tuple_vectors_to_json(const TupleVectors& outputs) {
                return tuple_vectors_to_json_impl(
                    outputs,
                    std::make_index_sequence<std::tuple_size_v<TupleVectors>>{}
                );
            }

            template<typename Result, typename Invoker>
            std::string execute_pointwise(std::size_t length, Invoker&& invoker) {
                using Decayed = std::decay_t<Result>;
                if constexpr (is_pair<Decayed>::value) {
                    using Left = typename Decayed::first_type;
                    using Right = typename Decayed::second_type;
                    std::vector<Left> leftValues;
                    std::vector<Right> rightValues;
                    leftValues.reserve(length);
                    rightValues.reserve(length);
                    for (std::size_t index = 0; index < length; ++index) {
                        auto value = invoker(index);
                        leftValues.push_back(value.first);
                        rightValues.push_back(value.second);
                    }
                    return "[" + to_json(leftValues) + "," + to_json(rightValues) + "]";
                } else if constexpr (is_tuple<Decayed>::value) {
                    auto outputs = make_tuple_of_vectors<Decayed>();
                    for (std::size_t index = 0; index < length; ++index) {
                        append_tuple_result(outputs, invoker(index));
                    }
                    return tuple_vectors_to_json(outputs);
                } else {
                    std::vector<Decayed> values;
                    values.reserve(length);
                    for (std::size_t index = 0; index < length; ++index) {
                        values.push_back(invoker(index));
                    }
                    return to_json(values);
                }
            }

            void write_response(const std::string& path, const std::string& resultJson) {
                std::ofstream output(path, std::ios::binary);
                if (!output.is_open()) {
                    throw std::runtime_error("无法写入 C++ 响应文件");
                }
                output << '{' << '"' << "result" << '"' << ':' << resultJson << '}';
            }

            int main() {
                try {
            """
        );

        for (CppFunctionSupport.CppParameterDescriptor parameter : function.parameters()) {
            Object argument = arguments.get(parameter.name());
            builder.append("        ")
                .append(buildVariableDeclaration(parameter, argument, pointwiseMode))
                .append('\n');
        }

        if (pointwiseMode) {
            builder.append('\n')
                .append("        std::size_t pointwiseLength = 0;\n");
            for (CppFunctionSupport.CppParameterDescriptor parameter : function.parameters()) {
                Object argument = arguments.get(parameter.name());
                if (argument instanceof List<?> && !parameter.sequenceType()) {
                    String variableName = buildVariableName(parameter.name());
                    builder.append("        if (pointwiseLength == 0) {\n")
                        .append("            pointwiseLength = ").append(variableName).append(".size();\n")
                        .append("        } else if (").append(variableName).append(".size() != pointwiseLength) {\n")
                        .append("            throw std::runtime_error(\"C++ 时序输入长度不一致，无法逐点调用模型\");\n")
                        .append("        }\n");
                }
            }
            builder.append("        if (pointwiseLength == 0) {\n")
                .append("            throw std::runtime_error(\"C++ 时序输入为空，无法逐点调用模型\");\n")
                .append("        }\n")
                .append("        auto invoke_point = [&](std::size_t pointIndex) {\n")
                .append("            return ").append(function.name()).append("(")
                .append(buildInvocationArguments(function, arguments, true))
                .append(");\n")
                .append("        };\n")
                .append("        using result_t = decltype(invoke_point(0));\n")
                .append("        write_response(\"").append(escapeCppString(RESPONSE_FILE_NAME)).append("\", ")
                .append("execute_pointwise<result_t>(pointwiseLength, invoke_point));\n");
        } else {
            builder.append('\n')
                .append("        auto result = ").append(function.name()).append("(")
                .append(buildInvocationArguments(function, arguments, false))
                .append(");\n")
                .append("        write_response(\"").append(escapeCppString(RESPONSE_FILE_NAME)).append("\", to_json(result));\n");
        }

        builder.append("""
                    return 0;
                } catch (const std::exception& ex) {
                    std::cerr << ex.what() << std::endl;
                    return 1;
                }
            }
            """);
        return builder.toString();
    }

    /**
     * 构建单个输入变量声明。
     */
    private String buildVariableDeclaration(CppFunctionSupport.CppParameterDescriptor parameter,
                                            Object argument,
                                            boolean pointwiseMode) {
        String variableName = buildVariableName(parameter.name());
        String filePath = INPUT_DIR_NAME + "/" + parameter.name() + ".txt";
        String storageType = canonicalizeType(parameter.storageType());
        String elementType = canonicalizeType(parameter.elementType());
        boolean sequenceInput = argument instanceof List<?>;

        if (pointwiseMode) {
            if (sequenceInput) {
                return "auto " + variableName + " = load_sequence<" + elementType + ">(\"" + escapeCppString(filePath) + "\");";
            }
            return storageType + " " + variableName + " = load_scalar<" + storageType + ">(\"" + escapeCppString(filePath) + "\");";
        }

        if (parameter.sequenceType()) {
            if (sequenceInput) {
                return storageType + " " + variableName + " = load_sequence<" + elementType + ">(\"" + escapeCppString(filePath) + "\");";
            }
            return storageType + " " + variableName + "{ load_scalar<" + elementType + ">(\"" + escapeCppString(filePath) + "\") };";
        }
        return storageType + " " + variableName + " = load_scalar<" + storageType + ">(\"" + escapeCppString(filePath) + "\");";
    }

    /**
     * 构建函数调用参数列表。
     */
    private String buildInvocationArguments(CppFunctionSupport.CppFunctionDescriptor function,
                                            LinkedHashMap<String, Object> arguments,
                                            boolean pointwiseMode) {
        List<String> parts = new ArrayList<>();
        for (CppFunctionSupport.CppParameterDescriptor parameter : function.parameters()) {
            String variableName = buildVariableName(parameter.name());
            Object argument = arguments.get(parameter.name());
            if (pointwiseMode && argument instanceof List<?> && !parameter.sequenceType()) {
                parts.add(variableName + ".at(pointIndex)");
            } else {
                parts.add(variableName);
            }
        }
        return String.join(", ", parts);
    }

    /**
     * 生成编译命令。
     */
    private List<String> buildCompileCommand(String sourceFileName, String runnerBinaryName) {
        return List.of(
            taskExecutionProperties.getCppCompilerExecutable(),
            "-std=c++17",
            "-O2",
            "-finput-charset=UTF-8",
            "-fexec-charset=UTF-8",
            sourceFileName,
            "-o",
            runnerBinaryName
        );
    }

    /**
     * 生成执行命令。
     */
    private List<String> buildRunCommand(Path runnerBinary) {
        return List.of(runnerBinary.toAbsolutePath().normalize().toString());
    }

    /**
     * 合并编译与运行日志。
     */
    private String mergeLogs(String compileLog, String runtimeLog) {
        boolean hasCompile = StringUtils.hasText(compileLog);
        boolean hasRuntime = StringUtils.hasText(runtimeLog);
        if (hasCompile && hasRuntime) {
            return "编译日志:\n" + compileLog.trim() + "\n\n运行日志:\n" + runtimeLog.trim();
        }
        if (hasCompile) {
            return "编译日志:\n" + compileLog.trim();
        }
        return hasRuntime ? runtimeLog.trim() : "";
    }

    /**
     * 规范化 C++ 类型字面量。
     */
    private String canonicalizeType(String type) {
        String value = StringUtils.hasText(type) ? type.trim() : "";
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("string")) {
            return "std::" + value;
        }
        if (lower.startsWith("vector<") || lower.startsWith("tuple<") || lower.startsWith("pair<")) {
            return "std::" + value;
        }
        return value;
    }

    /**
     * 构造变量名。
     */
    private String buildVariableName(String parameterName) {
        return "arg_" + parameterName;
    }

    /**
     * 解析模型临时文件名。
     */
    private String resolveCppFileName(String originalFileName, String functionName) {
        String base = StringUtils.hasText(originalFileName) ? originalFileName.trim() : "";
        int slashIndex = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slashIndex >= 0 && slashIndex + 1 < base.length()) {
            base = base.substring(slashIndex + 1);
        }
        if (!StringUtils.hasText(base)) {
            return functionName + ".cpp";
        }
        return base.toLowerCase(Locale.ROOT).endsWith(".cpp") ? base : base + ".cpp";
    }

    /**
     * 解析包装程序文件名。
     */
    private String resolveRunnerBinaryName() {
        return isWindows() ? "task_cpp_runner.exe" : "task_cpp_runner";
    }

    /**
     * 转义 C++ 字符串字面量。
     */
    private String escapeCppString(String value) {
        return String.valueOf(value)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }

    /**
     * 判断是否为 Windows。
     */
    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
