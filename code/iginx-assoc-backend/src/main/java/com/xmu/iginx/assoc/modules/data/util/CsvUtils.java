package com.xmu.iginx.assoc.modules.data.util;

import java.util.ArrayList;
import java.util.List;

/**
 * CSV 工具类，提供解析与序列化能力。
 */
public final class CsvUtils {

    private CsvUtils() {
    }

    /**
     * 解析一行 CSV，支持双引号转义。
     *
     * @param line CSV 行
     * @return 字段列表
     */
    public static List<String> parseLine(String line) {
        List<String> values = new ArrayList<>();
        if (line == null) {
            return values;
        }
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        // 双引号转义："" => "
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    values.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        values.add(current.toString());
        return values;
    }

    /**
     * 将值转换为 CSV 字段格式。
     *
     * @param value 原始值
     * @return CSV 字段
     */
    public static String toCsvValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        // 包含分隔符、引号或换行时需要使用双引号包裹
        boolean needQuote = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        String escaped = text.replace("\"", "\"\"");
        return needQuote ? "\"" + escaped + "\"" : escaped;
    }
}
