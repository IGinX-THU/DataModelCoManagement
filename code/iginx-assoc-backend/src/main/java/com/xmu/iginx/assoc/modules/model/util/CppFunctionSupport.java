package com.xmu.iginx.assoc.modules.model.util;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * C++ 函数解析辅助工具。
 * <p>
 * 负责从 `.cpp` 源文件中提取顶层函数定义、参数类型与返回类型，
 * 供模型上传解析、关联规则创建与任务执行阶段复用。
 * </p>
 */
public final class CppFunctionSupport {

    private static final Pattern CPP_TRAILING_IDENTIFIER_PATTERN =
        Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*$");

    private CppFunctionSupport() {
    }

    /**
     * 解析 C++ 顶层函数列表。
     *
     * @param text C++ 源码文本
     * @return 函数描述列表
     */
    public static List<CppFunctionDescriptor> listFunctions(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        String sanitized = sanitize(text);
        String[] lines = sanitized.split("\\R", -1);
        List<CppFunctionDescriptor> result = new ArrayList<>();
        int index = 0;
        int braceDepth = 0;
        while (index < lines.length) {
            String line = lines[index];
            String trimmed = line.trim();
            if (braceDepth != 0) {
                braceDepth += countBraceDelta(line);
                index++;
                continue;
            }
            if (!looksLikeFunctionStart(trimmed)) {
                braceDepth += countBraceDelta(line);
                index++;
                continue;
            }

            int startLine = index;
            int cursor = index;
            StringBuilder signatureBuilder = new StringBuilder(trimmed);
            int parenDepth = countBracketDelta(trimmed, '(', ')');
            while (cursor + 1 < lines.length && !isCompleteSignature(signatureBuilder.toString(), parenDepth)) {
                cursor++;
                String next = lines[cursor].trim();
                if (StringUtils.hasText(next)) {
                    if (signatureBuilder.length() > 0) {
                        signatureBuilder.append(' ');
                    }
                    signatureBuilder.append(next);
                }
                parenDepth += countBracketDelta(next, '(', ')');
            }

            String candidate = signatureBuilder.toString().trim();
            CppFunctionDescriptor descriptor = parseFunction(candidate, startLine + 1);
            if (descriptor != null && !"main".equalsIgnoreCase(descriptor.name())) {
                result.add(descriptor);
                index = findFunctionBodyEnd(lines, startLine, cursor);
                braceDepth = 0;
                continue;
            }
            index = cursor + 1;
        }
        return result;
    }

    /**
     * 按函数名查找 C++ 函数描述。
     *
     * @param text C++ 源码文本
     * @param functionName 函数名
     * @return 函数描述，不存在时返回 null
     */
    public static CppFunctionDescriptor findFunction(String text, String functionName) {
        if (!StringUtils.hasText(functionName)) {
            return null;
        }
        String target = functionName.trim();
        return listFunctions(text).stream()
            .filter(item -> target.equals(item.name()))
            .findFirst()
            .orElse(null);
    }

    /**
     * 判断是否为 tuple 返回类型。
     */
    public static boolean isTupleReturnType(String type) {
        String normalized = normalizeTypeToken(type).toLowerCase(Locale.ROOT);
        return normalized.startsWith("std::tuple<") || normalized.startsWith("tuple<");
    }

    /**
     * 判断是否为 pair 返回类型。
     */
    public static boolean isPairReturnType(String type) {
        String normalized = normalizeTypeToken(type).toLowerCase(Locale.ROOT);
        return normalized.startsWith("std::pair<") || normalized.startsWith("pair<");
    }

    /**
     * 判断是否为 void 返回类型。
     */
    public static boolean isVoidReturnType(String type) {
        return "void".equalsIgnoreCase(normalizeTypeToken(type));
    }

    /**
     * 拆分 tuple/pair 内部的类型列表。
     */
    public static List<String> splitReturnComponentTypes(String type) {
        String normalized = normalizeTypeToken(type);
        if (!StringUtils.hasText(normalized)) {
            return Collections.emptyList();
        }
        int openIndex = normalized.indexOf('<');
        int closeIndex = normalized.lastIndexOf('>');
        if (openIndex < 0 || closeIndex <= openIndex) {
            return Collections.emptyList();
        }
        return splitTopLevel(normalized.substring(openIndex + 1, closeIndex), ',');
    }

    /**
     * 将 C++ 类型映射为系统 Schema 类型。
     */
    public static String toSchemaType(String rawType) {
        String normalized = normalizeTypeToken(rawType).toLowerCase(Locale.ROOT);
        String compact = normalized.replace(" ", "");
        if (!StringUtils.hasText(normalized)) {
            return "STRING";
        }
        if (compact.startsWith("std::vector<") || compact.startsWith("vector<")
            || compact.startsWith("std::array<") || compact.startsWith("array<")) {
            return "ARRAY";
        }
        if (compact.startsWith("std::tuple<") || compact.startsWith("tuple<")
            || compact.startsWith("std::pair<") || compact.startsWith("pair<")
            || compact.startsWith("std::map<") || compact.startsWith("map<")
            || compact.startsWith("std::unordered_map<") || compact.startsWith("unordered_map<")) {
            return "OBJECT";
        }
        if (compact.contains("string") || "char".equals(compact) || "wchar_t".equals(compact)) {
            return "STRING";
        }
        if ("bool".equals(compact)) {
            return "BOOLEAN";
        }
        if ("float".equals(compact) || "double".equals(compact) || "longdouble".equals(compact)) {
            return "FLOAT";
        }
        if (compact.contains("int")
            || "short".equals(compact)
            || "long".equals(compact)
            || "longlong".equals(compact)
            || "size_t".equals(compact)
            || "ssize_t".equals(compact)) {
            return "INT";
        }
        return "STRING";
    }

    /**
     * 判断参数是否为受支持的序列类型。
     */
    public static boolean isSupportedSequenceType(String storageType) {
        String normalized = normalizeTypeToken(storageType).toLowerCase(Locale.ROOT);
        return normalized.startsWith("std::vector<") || normalized.startsWith("vector<");
    }

    /**
     * 判断参数是否支持当前 C++ 执行器自动装配。
     */
    public static boolean isExecutionSupportedType(String rawType, String storageType, String elementType) {
        if (!StringUtils.hasText(rawType) || !StringUtils.hasText(storageType)) {
            return false;
        }
        String compactRaw = rawType.replace(" ", "");
        if (compactRaw.contains("*") || compactRaw.contains("...") || compactRaw.contains("[")) {
            return false;
        }
        String normalized = normalizeTypeToken(storageType).toLowerCase(Locale.ROOT);
        if (isSupportedSequenceType(storageType)) {
            return isSupportedScalarExecutionType(elementType);
        }
        if (normalized.startsWith("std::array<") || normalized.startsWith("array<")) {
            return false;
        }
        return isSupportedScalarExecutionType(storageType);
    }

    /**
     * 提取受支持容器的元素类型。
     */
    public static String resolveElementType(String storageType) {
        String normalized = normalizeTypeToken(storageType);
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("std::vector<") || lower.startsWith("vector<")
            || lower.startsWith("std::array<") || lower.startsWith("array<")) {
            List<String> innerTypes = splitReturnComponentTypes(normalized);
            return innerTypes.isEmpty() ? "" : normalizeTypeToken(innerTypes.get(0));
        }
        return normalized;
    }

    private static boolean isSupportedScalarExecutionType(String type) {
        String normalized = normalizeTypeToken(type).toLowerCase(Locale.ROOT);
        String compact = normalized.replace(" ", "");
        return "bool".equals(compact)
            || "float".equals(compact)
            || "double".equals(compact)
            || "int".equals(compact)
            || "short".equals(compact)
            || "long".equals(compact)
            || "longlong".equals(compact)
            || "unsignedint".equals(compact)
            || "unsignedlong".equals(compact)
            || "unsignedlonglong".equals(compact)
            || "size_t".equals(compact)
            || compact.contains("string");
    }

    private static CppFunctionDescriptor parseFunction(String candidate, int lineNumber) {
        if (!containsFunctionBody(candidate)) {
            return null;
        }
        int bodyIndex = findTopLevelChar(candidate, '{');
        String signature = bodyIndex >= 0 ? candidate.substring(0, bodyIndex).trim() : candidate.trim();
        int openIndex = findTopLevelChar(signature, '(');
        if (openIndex < 0) {
            return null;
        }
        int closeIndex = findMatchingBracket(signature, openIndex, '(', ')');
        if (closeIndex <= openIndex) {
            return null;
        }
        String before = signature.substring(0, openIndex).trim();
        String after = signature.substring(closeIndex + 1).trim();
        if (!StringUtils.hasText(before)) {
            return null;
        }
        String functionName = extractTrailingIdentifier(before);
        if (!StringUtils.hasText(functionName) || isControlKeyword(functionName)) {
            return null;
        }
        String returnType = extractReturnType(before, functionName, after);
        if (!StringUtils.hasText(returnType)) {
            return null;
        }
        String argsText = signature.substring(openIndex + 1, closeIndex).trim();
        List<CppParameterDescriptor> parameters = parseParameters(argsText);
        if (parameters == null) {
            return null;
        }
        String normalizedSignature = signature.replaceAll("\\s+", " ").trim();
        return new CppFunctionDescriptor(
            functionName,
            functionName + " (line " + lineNumber + ")",
            normalizedSignature,
            lineNumber,
            returnType,
            parameters
        );
    }

    private static List<CppParameterDescriptor> parseParameters(String argsText) {
        if (!StringUtils.hasText(argsText) || "void".equalsIgnoreCase(argsText.trim())) {
            return Collections.emptyList();
        }
        List<String> parts = splitTopLevel(argsText, ',');
        List<CppParameterDescriptor> parameters = new ArrayList<>();
        for (String part : parts) {
            CppParameterDescriptor descriptor = parseParameter(part);
            if (descriptor == null) {
                return null;
            }
            parameters.add(descriptor);
        }
        return parameters;
    }

    private static CppParameterDescriptor parseParameter(String token) {
        String cleaned = stripAttributes(stripDefaultValue(token)).trim();
        if (!StringUtils.hasText(cleaned) || cleaned.contains("...")) {
            return null;
        }
        String name = extractTrailingIdentifier(cleaned);
        if (!StringUtils.hasText(name)) {
            return null;
        }
        int nameIndex = cleaned.lastIndexOf(name);
        if (nameIndex <= 0) {
            return null;
        }
        String rawType = cleaned.substring(0, nameIndex).trim();
        if (!StringUtils.hasText(rawType)) {
            return null;
        }
        String storageType = normalizeTypeToken(rawType);
        String elementType = resolveElementType(storageType);
        return new CppParameterDescriptor(
            name,
            rawType,
            storageType,
            toSchemaType(storageType),
            isSupportedSequenceType(storageType),
            elementType,
            isExecutionSupportedType(rawType, storageType, elementType)
        );
    }

    private static String extractReturnType(String before, String functionName, String after) {
        String trailing = "";
        int arrowIndex = findTopLevelArrow(after);
        if (arrowIndex >= 0) {
            trailing = normalizeTypeToken(after.substring(arrowIndex + 2).trim());
        }
        if (StringUtils.hasText(trailing)) {
            return trailing;
        }
        int nameIndex = before.lastIndexOf(functionName);
        if (nameIndex <= 0) {
            return "";
        }
        String rawType = before.substring(0, nameIndex).trim();
        return normalizeTypeToken(rawType);
    }

    private static String normalizeTypeToken(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return "";
        }
        String value = stripAttributes(rawType).trim();
        while (value.startsWith("template<")) {
            int openIndex = value.indexOf('<');
            int closeIndex = findMatchingBracket(value, openIndex, '<', '>');
            if (closeIndex < 0 || closeIndex + 1 >= value.length()) {
                break;
            }
            value = value.substring(closeIndex + 1).trim();
        }
        value = value.replaceAll("^(inline|static|constexpr|consteval|constinit|extern|friend|virtual)\\s+", "");
        value = value.replaceAll("\\b(const|volatile|mutable)\\b", " ");
        value = value.replace("&", " ");
        value = value.replaceAll("\\s*::\\s*", "::");
        value = value.replaceAll("\\s*<\\s*", "<");
        value = value.replaceAll("\\s*>\\s*", ">");
        value = value.replaceAll("\\s*,\\s*", ", ");
        value = value.replaceAll("\\s+", " ").trim();
        return value;
    }

    private static String stripDefaultValue(String text) {
        int index = findTopLevelChar(text, '=');
        return index >= 0 ? text.substring(0, index) : text;
    }

    private static String stripAttributes(String text) {
        String value = text == null ? "" : text.trim();
        while (value.startsWith("[[")) {
            int closeIndex = value.indexOf("]]");
            if (closeIndex < 0) {
                break;
            }
            value = value.substring(closeIndex + 2).trim();
        }
        while (value.startsWith("template<")) {
            int openIndex = value.indexOf('<');
            int closeIndex = findMatchingBracket(value, openIndex, '<', '>');
            if (closeIndex < 0 || closeIndex + 1 >= value.length()) {
                break;
            }
            value = value.substring(closeIndex + 1).trim();
        }
        return value;
    }

    private static String extractTrailingIdentifier(String text) {
        Matcher matcher = CPP_TRAILING_IDENTIFIER_PATTERN.matcher(text == null ? "" : text.trim());
        return matcher.find() ? matcher.group(1) : "";
    }

    private static boolean isControlKeyword(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return "if".equals(lower)
            || "for".equals(lower)
            || "while".equals(lower)
            || "switch".equals(lower)
            || "catch".equals(lower)
            || "return".equals(lower)
            || "sizeof".equals(lower)
            || "decltype".equals(lower);
    }

    private static boolean looksLikeFunctionStart(String trimmed) {
        if (!StringUtils.hasText(trimmed) || trimmed.startsWith("#")) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("if ")
            || lower.startsWith("for ")
            || lower.startsWith("while ")
            || lower.startsWith("switch ")
            || lower.startsWith("catch ")
            || lower.startsWith("return ")
            || lower.startsWith("namespace ")
            || lower.startsWith("class ")
            || lower.startsWith("struct ")
            || lower.startsWith("enum ")
            || lower.startsWith("using ")
            || lower.startsWith("typedef ")) {
            return false;
        }
        return trimmed.contains("(");
    }

    private static boolean isCompleteSignature(String text, int parenDepth) {
        return parenDepth <= 0 && (findTopLevelChar(text, '{') >= 0 || findTopLevelChar(text, ';') >= 0);
    }

    private static boolean containsFunctionBody(String text) {
        int bodyIndex = findTopLevelChar(text, '{');
        if (bodyIndex < 0) {
            return false;
        }
        int semicolonIndex = findTopLevelChar(text, ';');
        return semicolonIndex < 0 || bodyIndex < semicolonIndex;
    }

    private static int findFunctionBodyEnd(String[] lines, int startLine, int signatureEndLine) {
        int braceDepth = 0;
        boolean bodyStarted = false;
        for (int lineIndex = startLine; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            for (int index = 0; index < line.length(); index++) {
                char ch = line.charAt(index);
                if (ch == '{') {
                    braceDepth++;
                    bodyStarted = true;
                } else if (ch == '}' && bodyStarted) {
                    braceDepth--;
                    if (braceDepth == 0) {
                        return lineIndex + 1;
                    }
                }
            }
        }
        return signatureEndLine + 1;
    }

    private static int countBraceDelta(String text) {
        return countBracketDelta(text, '{', '}');
    }

    private static int countBracketDelta(String text, char open, char close) {
        int delta = 0;
        String safeText = text == null ? "" : text;
        for (int index = 0; index < safeText.length(); index++) {
            char ch = safeText.charAt(index);
            if (ch == open) {
                delta++;
            } else if (ch == close) {
                delta--;
            }
        }
        return delta;
    }

    private static int findTopLevelArrow(String text) {
        if (!StringUtils.hasText(text)) {
            return -1;
        }
        for (int index = 0; index < text.length() - 1; index++) {
            if (text.charAt(index) == '-' && text.charAt(index + 1) == '>') {
                return index;
            }
        }
        return -1;
    }

    private static int findTopLevelChar(String text, char target) {
        if (!StringUtils.hasText(text)) {
            return -1;
        }
        int round = 0;
        int square = 0;
        int curly = 0;
        int angle = 0;
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (singleQuoted || doubleQuoted) {
                if (ch == '\\') {
                    escaped = true;
                } else if (singleQuoted && ch == '\'') {
                    singleQuoted = false;
                } else if (doubleQuoted && ch == '"') {
                    doubleQuoted = false;
                }
                continue;
            }
            if (ch == '\'') {
                singleQuoted = true;
                continue;
            }
            if (ch == '"') {
                doubleQuoted = true;
                continue;
            }
            if (ch == '(') {
                if (target == '(' && round == 0 && square == 0 && curly == 0 && angle == 0) {
                    return index;
                }
                round++;
                continue;
            }
            if (ch == ')') {
                round = Math.max(0, round - 1);
                continue;
            }
            if (ch == '[') {
                if (index + 1 < text.length() && text.charAt(index + 1) == '[') {
                    continue;
                }
                if (target == '[' && round == 0 && square == 0 && curly == 0 && angle == 0) {
                    return index;
                }
                square++;
                continue;
            }
            if (ch == ']') {
                if (index > 0 && text.charAt(index - 1) == ']') {
                    continue;
                }
                square = Math.max(0, square - 1);
                continue;
            }
            if (ch == '{') {
                if (target == '{' && round == 0 && square == 0 && angle == 0 && curly == 0) {
                    return index;
                }
                curly++;
                continue;
            }
            if (ch == '}') {
                if (target == '}' && round == 0 && square == 0 && angle == 0 && curly == 1) {
                    return index;
                }
                curly = Math.max(0, curly - 1);
                continue;
            }
            if (ch == '<') {
                if (target == '<' && round == 0 && square == 0 && curly == 0 && angle == 0) {
                    return index;
                }
                angle++;
                continue;
            }
            if (ch == '>') {
                angle = Math.max(0, angle - 1);
                continue;
            }
            if (ch == target && round == 0 && square == 0 && curly == 0 && angle == 0) {
                return index;
            }
        }
        return -1;
    }

    private static int findMatchingBracket(String text, int openIndex, char open, char close) {
        if (!StringUtils.hasText(text) || openIndex < 0 || openIndex >= text.length() || text.charAt(openIndex) != open) {
            return -1;
        }
        int depth = 0;
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean escaped = false;
        for (int index = openIndex; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (singleQuoted || doubleQuoted) {
                if (ch == '\\') {
                    escaped = true;
                } else if (singleQuoted && ch == '\'') {
                    singleQuoted = false;
                } else if (doubleQuoted && ch == '"') {
                    doubleQuoted = false;
                }
                continue;
            }
            if (ch == '\'') {
                singleQuoted = true;
                continue;
            }
            if (ch == '"') {
                doubleQuoted = true;
                continue;
            }
            if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static List<String> splitTopLevel(String text, char separator) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int round = 0;
        int square = 0;
        int curly = 0;
        int angle = 0;
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
            if (singleQuoted || doubleQuoted) {
                current.append(ch);
                if (ch == '\\') {
                    escaped = true;
                } else if (singleQuoted && ch == '\'') {
                    singleQuoted = false;
                } else if (doubleQuoted && ch == '"') {
                    doubleQuoted = false;
                }
                continue;
            }
            if (ch == '\'') {
                singleQuoted = true;
                current.append(ch);
                continue;
            }
            if (ch == '"') {
                doubleQuoted = true;
                current.append(ch);
                continue;
            }
            if (ch == '(') {
                round++;
            } else if (ch == ')') {
                round = Math.max(0, round - 1);
            } else if (ch == '[') {
                square++;
            } else if (ch == ']') {
                square = Math.max(0, square - 1);
            } else if (ch == '{') {
                curly++;
            } else if (ch == '}') {
                curly = Math.max(0, curly - 1);
            } else if (ch == '<') {
                angle++;
            } else if (ch == '>') {
                angle = Math.max(0, angle - 1);
            } else if (ch == separator && round == 0 && square == 0 && curly == 0 && angle == 0) {
                String part = current.toString().trim();
                if (StringUtils.hasText(part)) {
                    parts.add(part);
                }
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        String tail = current.toString().trim();
        if (StringUtils.hasText(tail)) {
            parts.add(tail);
        }
        return parts;
    }

    private static String sanitize(String text) {
        StringBuilder result = new StringBuilder(text.length());
        boolean lineComment = false;
        boolean blockComment = false;
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            char next = index + 1 < text.length() ? text.charAt(index + 1) : '\0';
            if (lineComment) {
                if (ch == '\n' || ch == '\r') {
                    lineComment = false;
                    result.append(ch);
                } else {
                    result.append(' ');
                }
                continue;
            }
            if (blockComment) {
                if (ch == '*' && next == '/') {
                    result.append(' ').append(' ');
                    index++;
                    blockComment = false;
                } else {
                    result.append(ch == '\n' || ch == '\r' ? ch : ' ');
                }
                continue;
            }
            if (singleQuoted || doubleQuoted) {
                if (escaped) {
                    result.append(' ');
                    escaped = false;
                    continue;
                }
                if (ch == '\\') {
                    result.append(' ');
                    escaped = true;
                    continue;
                }
                if (singleQuoted && ch == '\'') {
                    singleQuoted = false;
                } else if (doubleQuoted && ch == '"') {
                    doubleQuoted = false;
                }
                result.append(ch == '\n' || ch == '\r' ? ch : ' ');
                continue;
            }
            if (ch == '/' && next == '/') {
                result.append(' ').append(' ');
                index++;
                lineComment = true;
                continue;
            }
            if (ch == '/' && next == '*') {
                result.append(' ').append(' ');
                index++;
                blockComment = true;
                continue;
            }
            if (ch == '\'') {
                result.append(' ');
                singleQuoted = true;
                continue;
            }
            if (ch == '"') {
                result.append(' ');
                doubleQuoted = true;
                continue;
            }
            result.append(ch);
        }
        return result.toString();
    }

    /**
     * C++ 函数描述。
     */
    public record CppFunctionDescriptor(String name,
                                        String displayName,
                                        String signature,
                                        Integer lineNumber,
                                        String returnType,
                                        List<CppParameterDescriptor> parameters) {
    }

    /**
     * C++ 参数描述。
     */
    public record CppParameterDescriptor(String name,
                                         String rawType,
                                         String storageType,
                                         String schemaType,
                                         boolean sequenceType,
                                         String elementType,
                                         boolean executionSupported) {
    }
}
