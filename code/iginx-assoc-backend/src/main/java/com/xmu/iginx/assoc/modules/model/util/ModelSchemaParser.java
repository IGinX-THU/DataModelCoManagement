package com.xmu.iginx.assoc.modules.model.util;

import com.xmu.iginx.assoc.modules.model.dto.ModelIoSchema;
import com.xmu.iginx.assoc.modules.model.dto.ModelSchemaParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ModelSchemaParser {

    private static final Pattern INPUT_PATTERN =
        Pattern.compile("@Input:\\s*(\\w+)\\s*(?:\\(([^)]+)\\))?\\s*(?:-\\s*(.*))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern OUTPUT_PATTERN =
        Pattern.compile("@Output:\\s*(\\w+)\\s*(?:\\(([^)]+)\\))?\\s*(?:-\\s*(.*))?", Pattern.CASE_INSENSITIVE);

    public ModelIoSchema parse(byte[] fileBytes) {
        ModelIoSchema schema = new ModelIoSchema();
        schema.setInputs(parseParams(fileBytes, INPUT_PATTERN, true));
        schema.setOutputs(parseParams(fileBytes, OUTPUT_PATTERN, false));
        schema.setDependencies(Collections.emptyList());
        return schema;
    }

    private List<ModelSchemaParam> parseParams(byte[] fileBytes, Pattern pattern, boolean defaultRequired) {
        String text = new String(fileBytes, StandardCharsets.UTF_8);
        List<ModelSchemaParam> params = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            ModelSchemaParam param = new ModelSchemaParam();
            param.setName(matcher.group(1));
            param.setType(normalizeType(matcher.group(2)));
            param.setDescription(StringUtils.hasText(matcher.group(3)) ? matcher.group(3).trim() : "");
            param.setUnit("-");
            param.setRequired(defaultRequired);
            params.add(param);
        }
        return params;
    }

    private String normalizeType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "STRING";
        }
        String value = raw.trim().toUpperCase();
        return switch (value) {
            case "FLOAT", "DOUBLE" -> "FLOAT";
            case "INT", "INTEGER", "LONG" -> "INT";
            case "BOOL", "BOOLEAN" -> "BOOLEAN";
            default -> value;
        };
    }
}
