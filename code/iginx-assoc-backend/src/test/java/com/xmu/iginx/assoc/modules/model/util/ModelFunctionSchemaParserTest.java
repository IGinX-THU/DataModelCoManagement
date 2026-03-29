package com.xmu.iginx.assoc.modules.model.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 模型函数结构解析器测试。
 */
class ModelFunctionSchemaParserTest {

    private final ModelFunctionSchemaParser parser = new ModelFunctionSchemaParser(new ModelSchemaParser());

    /**
     * 仅返回 Python 顶层函数。
     */
    @Test
    void listFunctions_shouldOnlyReturnTopLevelPythonFunctions() {
        String script = """
            def foo(x):
                return x

            class Demo:
                def inner(self, y):
                    return y

            def baz(a, b):
                return a + b
            """;

        List<ModelFunctionSchemaParser.FunctionMeta> functions = parser.listFunctions(bytes(script), "PY");

        assertEquals(2, functions.size());
        assertEquals("foo", functions.get(0).name());
        assertEquals("baz", functions.get(1).name());
    }

    /**
     * 解析 Python 输入输出类型。
     */
    @Test
    void parseByFunction_shouldParsePythonInputAndOutputTypes() {
        String script = """
            # @Input: b(FLOAT) - second input
            def calc(a: int, b) -> tuple[int, float]:
                return a, b
            """;

        ModelFunctionSchemaParser.ParseSchemaResult result = parser.parseByFunction(bytes(script), "PY", "calc");

        assertEquals(ModelFunctionSchemaParser.PARSE_MODE_SYNTAX, result.parseMode());
        assertEquals(2, result.schema().getInputs().size());
        assertEquals("INT", result.schema().getInputs().get(0).getType());
        assertEquals("FLOAT", result.schema().getInputs().get(1).getType());
        assertEquals(2, result.schema().getOutputs().size());
        assertEquals("out1", result.schema().getOutputs().get(0).getName());
        assertEquals("INT", result.schema().getOutputs().get(0).getType());
        assertEquals("FLOAT", result.schema().getOutputs().get(1).getType());
    }

    /**
     * 解析 Python 字典输出为多个字段。
     */
    @Test
    void parseByFunction_shouldExpandPythonDictOutputs() {
        String script = """
            def build():
                return {"temp": 36.5, "flag": True}
            """;

        ModelFunctionSchemaParser.ParseSchemaResult result = parser.parseByFunction(bytes(script), "PY", "build");

        assertEquals(ModelFunctionSchemaParser.PARSE_MODE_SYNTAX, result.parseMode());
        assertEquals(2, result.schema().getOutputs().size());
        assertEquals("temp", result.schema().getOutputs().get(0).getName());
        assertEquals("FLOAT", result.schema().getOutputs().get(0).getType());
        assertEquals("flag", result.schema().getOutputs().get(1).getName());
        assertEquals("BOOLEAN", result.schema().getOutputs().get(1).getType());
    }

    /**
     * 根据默认值推断 Python 输入类型。
     */
    @Test
    void parseByFunction_shouldInferPythonInputTypesFromDefaultValues() {
        String script = """
            def infer_types(a=1, b=1.5, c=True, d="ok", e=[1, 2], f={"x": 1}, g=None):
                return a
            """;

        ModelFunctionSchemaParser.ParseSchemaResult result = parser.parseByFunction(bytes(script), "PY", "infer_types");

        assertEquals(ModelFunctionSchemaParser.PARSE_MODE_SYNTAX, result.parseMode());
        assertEquals(7, result.schema().getInputs().size());
        assertEquals("INT", result.schema().getInputs().get(0).getType());
        assertEquals("FLOAT", result.schema().getInputs().get(1).getType());
        assertEquals("BOOLEAN", result.schema().getInputs().get(2).getType());
        assertEquals("STRING", result.schema().getInputs().get(3).getType());
        assertEquals("ARRAY", result.schema().getInputs().get(4).getType());
        assertEquals("OBJECT", result.schema().getInputs().get(5).getType());
        assertEquals("STRING", result.schema().getInputs().get(6).getType());
    }

    /**
     * 解析 MATLAB arguments 与输出类型。
     */
    @Test
    void parseByFunction_shouldParseMatlabArgumentsAndOutputs() {
        String script = """
            % @Output: y1(FLOAT) - output 1
            % @Output: y2(BOOLEAN) - output 2
            function [y1, y2] = runModel(temp, state)
            arguments
                temp (1,1) double
                state logical
            end
            y1 = temp;
            y2 = state;
            end
            """;

        ModelFunctionSchemaParser.ParseSchemaResult result = parser.parseByFunction(bytes(script), "MAT", "runModel");

        assertEquals(ModelFunctionSchemaParser.PARSE_MODE_SYNTAX, result.parseMode());
        assertEquals(2, result.schema().getInputs().size());
        assertEquals("FLOAT", result.schema().getInputs().get(0).getType());
        assertEquals("BOOLEAN", result.schema().getInputs().get(1).getType());
        assertEquals(2, result.schema().getOutputs().size());
        assertEquals("FLOAT", result.schema().getOutputs().get(0).getType());
        assertEquals("BOOLEAN", result.schema().getOutputs().get(1).getType());
    }

    /**
     * `.m` 后缀应被识别为 MATLAB 脚本。
     */
    @Test
    void listFunctions_shouldTreatMExtensionAsMatlab() {
        String script = """
            function result = predict_power(temperature, pressure, flow)
            arguments
                temperature (1,1) double
                pressure (1,1) double
                flow (1,1) double
            end
            result = 0.2 * temperature + 0.05 * pressure + 1.5 * flow;
            end
            """;

        List<ModelFunctionSchemaParser.FunctionMeta> functions = parser.listFunctions(bytes(script), "M");

        assertEquals(1, functions.size());
        assertEquals("predict_power", functions.get(0).name());
    }

    /**
     * `.m` 后缀下也应按 MATLAB 语法正确解析输入输出。
     */
    @Test
    void parseByFunction_shouldTreatMExtensionAsMatlab() {
        String script = """
            function result = predict_power(temperature, pressure, flow)
            arguments
                temperature (1,1) double
                pressure (1,1) double
                flow (1,1) double
            end
            result = 0.2 * temperature + 0.05 * pressure + 1.5 * flow;
            end
            """;

        ModelFunctionSchemaParser.ParseSchemaResult result =
            parser.parseByFunction(bytes(script), "M", "predict_power");

        assertEquals(ModelFunctionSchemaParser.PARSE_MODE_SYNTAX, result.parseMode());
        assertEquals(3, result.schema().getInputs().size());
        assertEquals("FLOAT", result.schema().getInputs().get(0).getType());
        assertEquals("FLOAT", result.schema().getInputs().get(1).getType());
        assertEquals("FLOAT", result.schema().getInputs().get(2).getType());
        assertEquals(1, result.schema().getOutputs().size());
        assertEquals("result", result.schema().getOutputs().get(0).getName());
        assertEquals("STRING", result.schema().getOutputs().get(0).getType());
    }

    /**
     * 仅返回 C++ 顶层函数，忽略类成员函数。
     */
    @Test
    void listFunctions_shouldOnlyReturnTopLevelCppFunctions() {
        String script = """
            #include <tuple>

            double predict_power(double temperature, double pressure) {
                return temperature * 0.2 + pressure;
            }

            class Demo {
            public:
                double inner(double x) {
                    return x;
                }
            };

            std::tuple<double, bool> classify(double value) {
                return { value * 0.5, value > 20.0 };
            }
            """;

        List<ModelFunctionSchemaParser.FunctionMeta> functions = parser.listFunctions(bytes(script), "CPP");

        assertEquals(2, functions.size());
        assertEquals("predict_power", functions.get(0).name());
        assertEquals("classify", functions.get(1).name());
    }

    /**
     * 解析 C++ 函数的输入与 tuple 输出。
     */
    @Test
    void parseByFunction_shouldParseCppInputsAndTupleOutputs() {
        String script = """
            #include <tuple>

            std::tuple<double, bool> classify(double temperature, bool enabled) {
                return { temperature * 0.5, enabled };
            }
            """;

        ModelFunctionSchemaParser.ParseSchemaResult result = parser.parseByFunction(bytes(script), "CPP", "classify");

        assertEquals(ModelFunctionSchemaParser.PARSE_MODE_SYNTAX, result.parseMode());
        assertEquals(2, result.schema().getInputs().size());
        assertEquals("FLOAT", result.schema().getInputs().get(0).getType());
        assertEquals("BOOLEAN", result.schema().getInputs().get(1).getType());
        assertEquals(2, result.schema().getOutputs().size());
        assertEquals("out1", result.schema().getOutputs().get(0).getName());
        assertEquals("FLOAT", result.schema().getOutputs().get(0).getType());
        assertEquals("out2", result.schema().getOutputs().get(1).getName());
        assertEquals("BOOLEAN", result.schema().getOutputs().get(1).getType());
    }

    /**
     * 语法解析失败时回退到注释解析。
     */
    @Test
    void parseByFunction_shouldFallbackToCommentWhenSyntaxHasNoIo() {
        String script = """
            # @Input: x(INT) - from comment
            # @Output: y(FLOAT) - from comment
            def fallback_case():
                pass
            """;

        ModelFunctionSchemaParser.ParseSchemaResult result = parser.parseByFunction(bytes(script), "PY", "fallback_case");

        assertEquals(ModelFunctionSchemaParser.PARSE_MODE_COMMENT_FALLBACK, result.parseMode());
        assertEquals(1, result.schema().getInputs().size());
        assertEquals(1, result.schema().getOutputs().size());
    }

    /**
     * 函数不存在时抛出异常。
     */
    @Test
    void parseByFunction_shouldThrowWhenFunctionNotFound() {
        String script = """
            def run(x):
                return x
            """;

        assertThrows(IllegalArgumentException.class, () -> parser.parseByFunction(bytes(script), "PY", "missing"));
    }

    /**
     * 将字符串转换为 UTF-8 字节数组。
     */
    private byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
