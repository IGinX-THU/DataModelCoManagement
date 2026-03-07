package com.xmu.iginx.assoc.modules.model.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelFunctionSchemaParserTest {

    private final ModelFunctionSchemaParser parser = new ModelFunctionSchemaParser(new ModelSchemaParser());

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

    @Test
    void parseByFunction_shouldThrowWhenFunctionNotFound() {
        String script = """
            def run(x):
                return x
            """;

        assertThrows(IllegalArgumentException.class, () -> parser.parseByFunction(bytes(script), "PY", "missing"));
    }

    private byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
