package com.xmu.iginx.assoc.modules.model.util;

import com.xmu.iginx.assoc.modules.model.dto.ModelIoSchema;
import com.xmu.iginx.assoc.modules.model.dto.ModelSchemaParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ModelFunctionSchemaParser {

    public static final String PARSE_MODE_SYNTAX = "SYNTAX";
    public static final String PARSE_MODE_COMMENT_FALLBACK = "COMMENT_FALLBACK";

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern PYTHON_RETURN_PATTERN = Pattern.compile("^return\\b(.*)$");
    private static final Pattern MATLAB_FUNCTION_PATTERN = Pattern.compile("^function\\s+(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MATLAB_IDENTIFIER_PREFIX_PATTERN = Pattern.compile("^([A-Za-z][A-Za-z0-9_]*)");

    private final ModelSchemaParser commentParser;

    public List<FunctionMeta> listFunctions(byte[] fileBytes, String fileType) {
        String text = toText(fileBytes);
        String normalizedType = normalizeFileType(fileType);
        if ("PY".equals(normalizedType)) {
            return parsePythonFunctions(text).stream()
                .map(item -> new FunctionMeta(item.name(), item.name() + " (line " + item.lineNumber() + ")", item.signature(), item.lineNumber()))
                .toList();
        }
        if ("MAT".equals(normalizedType)) {
            return parseMatlabFunctions(text).stream()
                .map(item -> new FunctionMeta(item.name(), item.name() + " (line " + item.lineNumber() + ")", item.signature(), item.lineNumber()))
                .toList();
        }
        return Collections.emptyList();
    }

    public ParseSchemaResult parseByFunction(byte[] fileBytes, String fileType, String functionName) {
        String normalizedType = normalizeFileType(fileType);
        ModelIoSchema fallbackSchema = safeCommentSchema(fileBytes);
        if (!"PY".equals(normalizedType) && !"MAT".equals(normalizedType)) {
            return fallbackResult(fallbackSchema, "Unsupported file type for syntax parsing, fallback to comments.");
        }
        try {
            String text = toText(fileBytes);
            ModelIoSchema syntaxSchema = "PY".equals(normalizedType)
                ? parsePythonSchema(text, functionName, fallbackSchema)
                : parseMatlabSchema(text, functionName, fallbackSchema);
            if (shouldFallback(syntaxSchema, fallbackSchema)) {
                return fallbackResult(fallbackSchema, "No IO extracted from syntax, fallback to comments.");
            }
            return syntaxResult(syntaxSchema, "Parsed from function syntax.");
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            return fallbackResult(fallbackSchema, "Syntax parsing failed, fallback to comments.");
        }
    }

    private ModelIoSchema parsePythonSchema(String text, String functionName, ModelIoSchema fallbackSchema) {
        List<PythonFunction> functions = parsePythonFunctions(text);
        PythonFunction target = functions.stream()
            .filter(item -> item.name().equals(functionName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Function not found: " + functionName));

        List<ModelSchemaParam> inputs = buildPythonInputs(target, toParamMap(fallbackSchema.getInputs()));
        List<ModelSchemaParam> outputs = buildPythonOutputs(text, target, safeList(fallbackSchema.getOutputs()), toParamMap(fallbackSchema.getOutputs()));
        ModelIoSchema schema = new ModelIoSchema();
        schema.setInputs(inputs);
        schema.setOutputs(outputs);
        schema.setDependencies(Collections.emptyList());
        return schema;
    }

    private ModelIoSchema parseMatlabSchema(String text, String functionName, ModelIoSchema fallbackSchema) {
        List<MatlabFunction> functions = parseMatlabFunctions(text);
        MatlabFunction target = functions.stream()
            .filter(item -> item.name().equals(functionName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Function not found: " + functionName));

        Map<String, ModelSchemaParam> inputComments = toParamMap(fallbackSchema.getInputs());
        List<ModelSchemaParam> outputComments = safeList(fallbackSchema.getOutputs());
        Map<String, ModelSchemaParam> outputCommentMap = toParamMap(outputComments);
        Map<String, String> argTypes = parseMatlabArgumentTypes(text, target);

        List<ModelSchemaParam> inputs = new ArrayList<>();
        for (String name : target.inputs()) {
            ModelSchemaParam comment = inputComments.get(name.toLowerCase(Locale.ROOT));
            ModelSchemaParam param = new ModelSchemaParam();
            param.setName(name);
            param.setType(resolveMatlabInputType(name, argTypes, comment));
            param.setUnit(comment != null && StringUtils.hasText(comment.getUnit()) ? comment.getUnit() : "-");
            param.setDescription(comment != null && StringUtils.hasText(comment.getDescription()) ? comment.getDescription() : "");
            param.setRequired(true);
            inputs.add(param);
        }

        List<ModelSchemaParam> outputs = new ArrayList<>();
        for (int index = 0; index < target.outputs().size(); index++) {
            String name = target.outputs().get(index);
            ModelSchemaParam comment = resolveCommentOutputByIndexOrName(outputComments, outputCommentMap, index, name);
            outputs.add(buildOutputParam(name, comment != null ? comment.getType() : "STRING", comment));
        }

        ModelIoSchema schema = new ModelIoSchema();
        schema.setInputs(inputs);
        schema.setOutputs(outputs);
        schema.setDependencies(Collections.emptyList());
        return schema;
    }

    private List<ModelSchemaParam> buildPythonInputs(PythonFunction function, Map<String, ModelSchemaParam> commentMap) {
        List<ModelSchemaParam> params = new ArrayList<>();
        for (String rawArg : splitTopLevel(function.argsText(), ',')) {
            String token = stripInlineComment(rawArg).trim();
            if (!StringUtils.hasText(token) || "/".equals(token) || "*".equals(token)) {
                continue;
            }
            ParsedArgument arg = parsePythonArgument(token);
            if (!isIdentifier(arg.name())) {
                continue;
            }
            ModelSchemaParam comment = commentMap.get(arg.name().toLowerCase(Locale.ROOT));
            ModelSchemaParam param = new ModelSchemaParam();
            param.setName(arg.name());
            if (StringUtils.hasText(arg.typeAnnotation())) {
                param.setType(normalizeType(arg.typeAnnotation()));
            } else if (comment != null && StringUtils.hasText(comment.getType())) {
                param.setType(normalizeType(comment.getType()));
            } else if (StringUtils.hasText(arg.defaultValue())) {
                String inferred = inferExpressionType(arg.defaultValue());
                if (StringUtils.hasText(inferred)) {
                    param.setType(normalizeType(inferred));
                } else {
                    param.setType("STRING");
                }
            } else {
                param.setType("STRING");
            }
            param.setUnit(comment != null && StringUtils.hasText(comment.getUnit()) ? comment.getUnit() : "-");
            param.setDescription(comment != null && StringUtils.hasText(comment.getDescription()) ? comment.getDescription() : "");
            param.setRequired(true);
            params.add(param);
        }
        return params;
    }

    private List<ModelSchemaParam> buildPythonOutputs(String text,
                                                      PythonFunction function,
                                                      List<ModelSchemaParam> commentOutputs,
                                                      Map<String, ModelSchemaParam> commentOutputMap) {
        String returnExpression = extractPythonReturnExpression(text, function);
        List<String> annotatedTypes = parsePythonReturnTypes(function.returnAnnotation());
        if (!StringUtils.hasText(returnExpression)) {
            return Collections.emptyList();
        }

        List<ModelSchemaParam> outputs = new ArrayList<>();
        if (isDictLiteral(returnExpression)) {
            List<Map.Entry<String, String>> dictEntries = parseDictEntries(returnExpression);
            for (int index = 0; index < dictEntries.size(); index++) {
                Map.Entry<String, String> entry = dictEntries.get(index);
                String name = StringUtils.hasText(entry.getKey()) ? entry.getKey() : "out" + (index + 1);
                String type = resolveOutputType(index, name, annotatedTypes, inferExpressionType(entry.getValue()), commentOutputs, commentOutputMap);
                ModelSchemaParam comment = resolveCommentOutputByIndexOrName(commentOutputs, commentOutputMap, index, name);
                outputs.add(buildOutputParam(name, type, comment));
            }
            if (!outputs.isEmpty()) {
                return outputs;
            }
        }

        List<String> expressions = parseTupleOrListExpressions(returnExpression);
        if (expressions.size() > 1) {
            for (int index = 0; index < expressions.size(); index++) {
                String name = "out" + (index + 1);
                String type = resolveOutputType(index, name, annotatedTypes, inferExpressionType(expressions.get(index)), commentOutputs, commentOutputMap);
                ModelSchemaParam comment = resolveCommentOutputByIndexOrName(commentOutputs, commentOutputMap, index, name);
                outputs.add(buildOutputParam(name, type, comment));
            }
            return outputs;
        }

        String type = resolveOutputType(0, "result", annotatedTypes, inferExpressionType(returnExpression), commentOutputs, commentOutputMap);
        ModelSchemaParam comment = resolveCommentOutputByIndexOrName(commentOutputs, commentOutputMap, 0, "result");
        outputs.add(buildOutputParam("result", type, comment));
        return outputs;
    }

    private ModelSchemaParam buildOutputParam(String name, String type, ModelSchemaParam comment) {
        ModelSchemaParam output = new ModelSchemaParam();
        output.setName(name);
        output.setType(normalizeType(type));
        output.setUnit(comment != null && StringUtils.hasText(comment.getUnit()) ? comment.getUnit() : "-");
        output.setDescription(comment != null && StringUtils.hasText(comment.getDescription()) ? comment.getDescription() : "");
        output.setRequired(false);
        return output;
    }

    private String resolveMatlabInputType(String name, Map<String, String> argTypes, ModelSchemaParam comment) {
        String type = argTypes.get(name.toLowerCase(Locale.ROOT));
        if (StringUtils.hasText(type)) {
            return normalizeType(type);
        }
        if (comment != null && StringUtils.hasText(comment.getType())) {
            return normalizeType(comment.getType());
        }
        return "STRING";
    }

    private String resolveOutputType(int index, String name, List<String> annotatedTypes, String inferredType,
                                     List<ModelSchemaParam> commentOutputs, Map<String, ModelSchemaParam> commentOutputMap) {
        if (index < annotatedTypes.size() && StringUtils.hasText(annotatedTypes.get(index))) {
            return normalizeType(annotatedTypes.get(index));
        }
        if (StringUtils.hasText(inferredType)) {
            return normalizeType(inferredType);
        }
        ModelSchemaParam byName = commentOutputMap.get(name.toLowerCase(Locale.ROOT));
        if (byName != null && StringUtils.hasText(byName.getType())) {
            return normalizeType(byName.getType());
        }
        if (index < commentOutputs.size() && StringUtils.hasText(commentOutputs.get(index).getType())) {
            return normalizeType(commentOutputs.get(index).getType());
        }
        return "STRING";
    }

    private Map<String, String> parseMatlabArgumentTypes(String text, MatlabFunction function) {
        String[] lines = text.split("\\R", -1);
        Map<String, String> types = new LinkedHashMap<>();
        boolean inArguments = false;
        for (int lineIndex = function.bodyStartIndex(); lineIndex < function.bodyEndIndex() && lineIndex < lines.length; lineIndex++) {
            String line = stripMatlabComment(lines[lineIndex]).trim();
            if (!StringUtils.hasText(line)) {
                continue;
            }
            if (!inArguments) {
                if (line.toLowerCase(Locale.ROOT).startsWith("arguments")) {
                    inArguments = true;
                }
                continue;
            }
            if ("end".equalsIgnoreCase(line)) {
                break;
            }
            Matcher matcher = MATLAB_IDENTIFIER_PREFIX_PATTERN.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            String argName = matcher.group(1).toLowerCase(Locale.ROOT);
            String remain = line.substring(matcher.end()).trim();
            if (remain.startsWith("(")) {
                int closeIndex = findMatchingBracket(remain, 0, '(', ')');
                remain = closeIndex >= 0 && closeIndex + 1 < remain.length() ? remain.substring(closeIndex + 1).trim() : "";
            }
            Matcher typeMatcher = MATLAB_IDENTIFIER_PREFIX_PATTERN.matcher(remain);
            if (typeMatcher.find()) {
                types.put(argName, typeMatcher.group(1));
            }
        }
        return types;
    }

    private List<PythonFunction> parsePythonFunctions(String text) {
        List<PythonFunction> functions = new ArrayList<>();
        String[] lines = text.split("\\R", -1);
        int index = 0;
        while (index < lines.length) {
            String raw = lines[index];
            String trimmed = raw.trim();
            if (countLeadingSpaces(raw) != 0 || !trimmed.startsWith("def ")) {
                index++;
                continue;
            }
            int signatureEnd = index;
            StringBuilder signature = new StringBuilder(trimmed);
            int balance = bracketDelta(trimmed);
            while ((balance > 0 || !trimmed.endsWith(":")) && signatureEnd + 1 < lines.length) {
                signatureEnd++;
                trimmed = lines[signatureEnd].trim();
                signature.append(" ").append(trimmed);
                balance += bracketDelta(trimmed);
            }
            PythonFunction function = parsePythonFunction(signature.toString(), index + 1);
            if (function == null) {
                index++;
                continue;
            }
            int bodyStart = signatureEnd + 1;
            int bodyEnd = lines.length;
            for (int cursor = bodyStart; cursor < lines.length; cursor++) {
                String candidate = lines[cursor];
                if (StringUtils.hasText(candidate.trim()) && countLeadingSpaces(candidate) == 0) {
                    bodyEnd = cursor;
                    break;
                }
            }
            functions.add(new PythonFunction(function.name(), function.signature(), function.lineNumber(),
                function.argsText(), function.returnAnnotation(), bodyStart, bodyEnd));
            index = signatureEnd + 1;
        }
        return functions;
    }

    private PythonFunction parsePythonFunction(String signature, int lineNumber) {
        String normalized = signature.trim().replaceAll("\\s+", " ");
        if (!normalized.startsWith("def ")) {
            return null;
        }
        int openIndex = normalized.indexOf('(');
        int colonIndex = normalized.lastIndexOf(':');
        if (openIndex <= 4 || colonIndex < 0) {
            return null;
        }
        String name = normalized.substring(4, openIndex).trim();
        if (!isIdentifier(name)) {
            return null;
        }
        int closeIndex = findMatchingBracket(normalized, openIndex, '(', ')');
        if (closeIndex < 0 || closeIndex >= colonIndex) {
            return null;
        }
        String args = normalized.substring(openIndex + 1, closeIndex).trim();
        String annotation = "";
        int arrowIndex = normalized.indexOf("->", closeIndex);
        if (arrowIndex >= 0 && arrowIndex < colonIndex) {
            annotation = normalized.substring(arrowIndex + 2, colonIndex).trim();
        }
        return new PythonFunction(name, normalized, lineNumber, args, annotation, 0, 0);
    }

    private List<MatlabFunction> parseMatlabFunctions(String text) {
        List<MatlabFunction> functions = new ArrayList<>();
        String[] lines = text.split("\\R", -1);
        int index = 0;
        while (index < lines.length) {
            String raw = lines[index];
            String trimmed = raw.trim();
            if (countLeadingSpaces(raw) != 0 || !trimmed.toLowerCase(Locale.ROOT).startsWith("function")) {
                index++;
                continue;
            }
            MatlabFunction function = parseMatlabFunction(trimmed, index + 1);
            if (function == null) {
                index++;
                continue;
            }
            int bodyStart = index + 1;
            int bodyEnd = lines.length;
            for (int cursor = bodyStart; cursor < lines.length; cursor++) {
                String candidate = lines[cursor];
                String cTrim = candidate.trim();
                if (StringUtils.hasText(cTrim) && countLeadingSpaces(candidate) == 0 && cTrim.toLowerCase(Locale.ROOT).startsWith("function")) {
                    bodyEnd = cursor;
                    break;
                }
            }
            functions.add(new MatlabFunction(function.name(), function.signature(), function.lineNumber(),
                function.inputs(), function.outputs(), bodyStart, bodyEnd));
            index++;
        }
        return functions;
    }

    private MatlabFunction parseMatlabFunction(String signature, int lineNumber) {
        Matcher matcher = MATLAB_FUNCTION_PATTERN.matcher(signature.trim());
        if (!matcher.find()) {
            return null;
        }
        String content = matcher.group(1).trim();
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String outputPart = "";
        String functionPart = content;
        int equalIndex = content.indexOf('=');
        if (equalIndex >= 0) {
            outputPart = content.substring(0, equalIndex).trim();
            functionPart = content.substring(equalIndex + 1).trim();
        }
        String functionName = extractPrefixIdentifier(functionPart);
        if (!StringUtils.hasText(functionName)) {
            return null;
        }
        List<String> outputs = parseMatlabOutputNames(outputPart);
        String argsText = "";
        int openIndex = functionPart.indexOf('(');
        if (openIndex >= 0) {
            int closeIndex = findMatchingBracket(functionPart, openIndex, '(', ')');
            if (closeIndex > openIndex) {
                argsText = functionPart.substring(openIndex + 1, closeIndex);
            }
        }
        List<String> inputs = splitTopLevel(argsText, ',').stream()
            .map(this::extractPrefixIdentifier)
            .filter(StringUtils::hasText)
            .toList();
        return new MatlabFunction(functionName, signature, lineNumber, inputs, outputs, 0, 0);
    }

    private List<String> parseMatlabOutputNames(String outputPart) {
        if (!StringUtils.hasText(outputPart)) {
            return Collections.emptyList();
        }
        String trimmed = outputPart.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return splitTopLevel(trimmed.substring(1, trimmed.length() - 1), ',').stream()
                .map(this::extractPrefixIdentifier)
                .filter(StringUtils::hasText)
                .toList();
        }
        String single = extractPrefixIdentifier(trimmed);
        return StringUtils.hasText(single) ? List.of(single) : Collections.emptyList();
    }

    private ParsedArgument parsePythonArgument(String token) {
        String cleaned = token.replaceAll("^\\*\\*?", "").trim();
        int assignIndex = findTopLevelChar(cleaned, '=');
        String defaultValue = assignIndex >= 0 ? cleaned.substring(assignIndex + 1).trim() : "";
        String left = assignIndex >= 0 ? cleaned.substring(0, assignIndex).trim() : cleaned;
        int colonIndex = findTopLevelChar(left, ':');
        if (colonIndex >= 0) {
            return new ParsedArgument(left.substring(0, colonIndex).trim(), left.substring(colonIndex + 1).trim(), defaultValue);
        }
        return new ParsedArgument(left, "", defaultValue);
    }

    private String extractPythonReturnExpression(String text, PythonFunction function) {
        String[] lines = text.split("\\R", -1);
        for (int lineIndex = function.bodyStartIndex(); lineIndex < function.bodyEndIndex() && lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex].trim();
            Matcher matcher = PYTHON_RETURN_PATTERN.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            String expression = matcher.group(1).trim();
            if (!StringUtils.hasText(expression)) {
                return "";
            }
            int balance = bracketDelta(expression);
            int cursor = lineIndex;
            while (balance > 0 && cursor + 1 < function.bodyEndIndex() && cursor + 1 < lines.length) {
                cursor++;
                String nextLine = lines[cursor].trim();
                expression = expression + " " + nextLine;
                balance += bracketDelta(nextLine);
            }
            return expression.trim();
        }
        return "";
    }

    private List<String> parsePythonReturnTypes(String annotation) {
        if (!StringUtils.hasText(annotation)) {
            return Collections.emptyList();
        }
        String value = annotation.trim();
        int unionIndex = findTopLevelChar(value, '|');
        if (unionIndex > 0) {
            value = value.substring(0, unionIndex).trim();
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("typing.tuple[") || lower.startsWith("tuple[")) {
            return splitTopLevel(extractGenericInner(value), ',').stream().map(this::normalizeType).toList();
        }
        if (lower.startsWith("typing.list[") || lower.startsWith("list[")) {
            String inner = extractGenericInner(value);
            return StringUtils.hasText(inner) ? List.of(normalizeType(inner)) : Collections.emptyList();
        }
        return List.of(normalizeType(value));
    }

    private List<Map.Entry<String, String>> parseDictEntries(String expression) {
        String body = expression.trim();
        if (body.startsWith("{") && body.endsWith("}")) {
            body = body.substring(1, body.length() - 1).trim();
        }
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        for (String item : splitTopLevel(body, ',')) {
            int colonIndex = findTopLevelChar(item, ':');
            if (colonIndex <= 0) {
                continue;
            }
            entries.add(Map.entry(stripQuote(item.substring(0, colonIndex).trim()), item.substring(colonIndex + 1).trim()));
        }
        return entries;
    }

    private List<String> parseTupleOrListExpressions(String expression) {
        String trimmed = expression.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            List<String> values = splitTopLevel(trimmed.substring(1, trimmed.length() - 1), ',');
            return values.size() > 1 ? values : Collections.singletonList(trimmed);
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            List<String> values = splitTopLevel(trimmed.substring(1, trimmed.length() - 1), ',');
            return values.size() > 1 ? values : Collections.singletonList(trimmed);
        }
        List<String> direct = splitTopLevel(trimmed, ',');
        return direct.size() > 1 ? direct : Collections.singletonList(trimmed);
    }

    private List<String> splitTopLevel(String text, char splitChar) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int round = 0;
        int square = 0;
        int curly = 0;
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (escaped) {
                current.append(ch);
                escaped = false;
                continue;
            }
            if (ch == '\\' && (singleQuoted || doubleQuoted)) {
                current.append(ch);
                escaped = true;
                continue;
            }
            if (ch == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
                current.append(ch);
                continue;
            }
            if (ch == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
                current.append(ch);
                continue;
            }
            if (!singleQuoted && !doubleQuoted) {
                if (ch == '(') round++;
                else if (ch == ')') round = Math.max(0, round - 1);
                else if (ch == '[') square++;
                else if (ch == ']') square = Math.max(0, square - 1);
                else if (ch == '{') curly++;
                else if (ch == '}') curly = Math.max(0, curly - 1);
                else if (ch == splitChar && round == 0 && square == 0 && curly == 0) {
                    String part = current.toString().trim();
                    if (StringUtils.hasText(part)) parts.add(part);
                    current.setLength(0);
                    continue;
                }
            }
            current.append(ch);
        }
        String tail = current.toString().trim();
        if (StringUtils.hasText(tail)) parts.add(tail);
        return parts;
    }

    private int findTopLevelChar(String text, char target) {
        int round = 0;
        int square = 0;
        int curly = 0;
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
                continue;
            }
            if (ch == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
                continue;
            }
            if (singleQuoted || doubleQuoted) continue;
            if (ch == '(') round++;
            else if (ch == ')') round = Math.max(0, round - 1);
            else if (ch == '[') square++;
            else if (ch == ']') square = Math.max(0, square - 1);
            else if (ch == '{') curly++;
            else if (ch == '}') curly = Math.max(0, curly - 1);
            else if (ch == target && round == 0 && square == 0 && curly == 0) return index;
        }
        return -1;
    }

    private int findMatchingBracket(String text, int openIndex, char open, char close) {
        if (openIndex < 0 || openIndex >= text.length() || text.charAt(openIndex) != open) {
            return -1;
        }
        int depth = 0;
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        for (int index = openIndex; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
                continue;
            }
            if (ch == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
                continue;
            }
            if (singleQuoted || doubleQuoted) continue;
            if (ch == open) depth++;
            else if (ch == close) {
                depth--;
                if (depth == 0) return index;
            }
        }
        return -1;
    }

    private int bracketDelta(String text) {
        int delta = 0;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '(' || ch == '[' || ch == '{') delta++;
            else if (ch == ')' || ch == ']' || ch == '}') delta--;
        }
        return delta;
    }

    private int countLeadingSpaces(String line) {
        int count = 0;
        for (int index = 0; index < line.length(); index++) {
            char ch = line.charAt(index);
            if (ch == ' ') count++;
            else if (ch == '\t') count += 4;
            else break;
        }
        return count;
    }

    private String extractPrefixIdentifier(String text) {
        Matcher matcher = MATLAB_IDENTIFIER_PREFIX_PATTERN.matcher(text == null ? "" : text.trim());
        return matcher.find() ? matcher.group(1) : "";
    }

    private String stripInlineComment(String token) {
        int commentIndex = findTopLevelChar(token, '#');
        return commentIndex >= 0 ? token.substring(0, commentIndex) : token;
    }

    private String stripMatlabComment(String line) {
        int commentIndex = line.indexOf('%');
        return commentIndex >= 0 ? line.substring(0, commentIndex) : line;
    }

    private String stripQuote(String text) {
        if (!StringUtils.hasText(text)) return "";
        if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private String extractGenericInner(String annotation) {
        int openIndex = annotation.indexOf('[');
        return openIndex >= 0 && annotation.endsWith("]") ? annotation.substring(openIndex + 1, annotation.length() - 1).trim() : "";
    }

    private String inferExpressionType(String expression) {
        if (!StringUtils.hasText(expression)) return "";
        String value = expression.trim();
        if (value.matches("^[+-]?\\d+$")) return "INT";
        if (value.matches("^[+-]?(\\d+\\.\\d*|\\d*\\.\\d+)([eE][+-]?\\d+)?$")) return "FLOAT";
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) return "BOOLEAN";
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) return "STRING";
        if (value.startsWith("{") && value.endsWith("}")) return "OBJECT";
        if ((value.startsWith("[") && value.endsWith("]")) || (value.startsWith("(") && value.endsWith(")"))) return "ARRAY";
        return "";
    }

    private String normalizeType(String raw) {
        if (!StringUtils.hasText(raw)) return "STRING";
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "FLOAT", "DOUBLE", "REAL", "SINGLE", "DECIMAL" -> "FLOAT";
            case "INT", "INTEGER", "LONG", "SHORT", "UINT8", "UINT16", "UINT32", "INT8", "INT16", "INT32", "INT64" -> "INT";
            case "BOOL", "BOOLEAN", "LOGICAL" -> "BOOLEAN";
            case "STR", "STRING", "CHAR", "TEXT" -> "STRING";
            default -> value;
        };
    }

    private String normalizeFileType(String fileType) {
        return fileType == null ? "" : fileType.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isDictLiteral(String expression) {
        String value = expression == null ? "" : expression.trim();
        return value.startsWith("{") && value.endsWith("}");
    }

    private boolean isIdentifier(String name) {
        return IDENTIFIER_PATTERN.matcher(name == null ? "" : name).matches();
    }

    private Map<String, ModelSchemaParam> toParamMap(List<ModelSchemaParam> params) {
        Map<String, ModelSchemaParam> map = new LinkedHashMap<>();
        for (ModelSchemaParam param : safeList(params)) {
            if (param == null || !StringUtils.hasText(param.getName())) continue;
            map.put(param.getName().toLowerCase(Locale.ROOT), param);
        }
        return map;
    }

    private List<ModelSchemaParam> safeList(List<ModelSchemaParam> params) {
        return params == null ? Collections.emptyList() : params;
    }

    private List<ModelSchemaParam> copyParams(List<ModelSchemaParam> params, boolean required) {
        List<ModelSchemaParam> copied = new ArrayList<>();
        for (ModelSchemaParam source : safeList(params)) {
            ModelSchemaParam target = new ModelSchemaParam();
            target.setName(source.getName());
            target.setType(normalizeType(source.getType()));
            target.setUnit(StringUtils.hasText(source.getUnit()) ? source.getUnit() : "-");
            target.setDescription(StringUtils.hasText(source.getDescription()) ? source.getDescription() : "");
            target.setRequired(required);
            copied.add(target);
        }
        return copied;
    }

    private ModelSchemaParam resolveCommentOutputByIndexOrName(List<ModelSchemaParam> comments,
                                                               Map<String, ModelSchemaParam> commentMap,
                                                               int index,
                                                               String name) {
        ModelSchemaParam byName = commentMap.get(name.toLowerCase(Locale.ROOT));
        if (byName != null) return byName;
        return index >= 0 && index < comments.size() ? comments.get(index) : null;
    }

    private boolean shouldFallback(ModelIoSchema syntaxSchema, ModelIoSchema fallbackSchema) {
        if (isSchemaEmpty(fallbackSchema)) {
            return false;
        }
        List<ModelSchemaParam> syntaxInputs = syntaxSchema == null ? Collections.emptyList() : safeList(syntaxSchema.getInputs());
        List<ModelSchemaParam> syntaxOutputs = syntaxSchema == null ? Collections.emptyList() : safeList(syntaxSchema.getOutputs());
        boolean missingInput = syntaxInputs.isEmpty() && !safeList(fallbackSchema.getInputs()).isEmpty();
        boolean missingOutput = syntaxOutputs.isEmpty() && !safeList(fallbackSchema.getOutputs()).isEmpty();
        return isSchemaEmpty(syntaxSchema) || missingInput || missingOutput;
    }

    private boolean isSchemaEmpty(ModelIoSchema schema) {
        return schema == null || (safeList(schema.getInputs()).isEmpty() && safeList(schema.getOutputs()).isEmpty());
    }

    private ModelIoSchema safeCommentSchema(byte[] fileBytes) {
        try {
            ModelIoSchema schema = commentParser.parse(fileBytes);
            if (schema.getDependencies() == null) schema.setDependencies(Collections.emptyList());
            return schema;
        } catch (Exception ex) {
            ModelIoSchema schema = new ModelIoSchema();
            schema.setInputs(Collections.emptyList());
            schema.setOutputs(Collections.emptyList());
            schema.setDependencies(Collections.emptyList());
            return schema;
        }
    }

    private ParseSchemaResult fallbackResult(ModelIoSchema schema, String message) {
        if (schema.getInputs() == null) schema.setInputs(Collections.emptyList());
        if (schema.getOutputs() == null) schema.setOutputs(Collections.emptyList());
        if (schema.getDependencies() == null) schema.setDependencies(Collections.emptyList());
        return new ParseSchemaResult(schema, PARSE_MODE_COMMENT_FALLBACK, message);
    }

    private ParseSchemaResult syntaxResult(ModelIoSchema schema, String message) {
        if (schema.getDependencies() == null) schema.setDependencies(Collections.emptyList());
        return new ParseSchemaResult(schema, PARSE_MODE_SYNTAX, message);
    }

    private String toText(byte[] fileBytes) {
        return new String(fileBytes == null ? new byte[0] : fileBytes, StandardCharsets.UTF_8);
    }

    private record ParsedArgument(String name, String typeAnnotation, String defaultValue) {
    }

    private record PythonFunction(String name,
                                  String signature,
                                  int lineNumber,
                                  String argsText,
                                  String returnAnnotation,
                                  int bodyStartIndex,
                                  int bodyEndIndex) {
    }

    private record MatlabFunction(String name,
                                  String signature,
                                  int lineNumber,
                                  List<String> inputs,
                                  List<String> outputs,
                                  int bodyStartIndex,
                                  int bodyEndIndex) {
    }

    public record FunctionMeta(String name, String displayName, String signature, Integer lineNumber) {
    }

    public record ParseSchemaResult(ModelIoSchema schema, String parseMode, String message) {
    }
}
