package com.xmu.iginx.assoc.modules.data.util;

import cn.edu.tsinghua.iginx.thrift.DataType;

import java.nio.charset.StandardCharsets;

public final class IginxDataTypeConverter {

    private IginxDataTypeConverter() {
    }

    public static DataType parseType(String rawType) {
        if (rawType == null) {
            return DataType.DOUBLE;
        }
        return switch (rawType.trim().toUpperCase()) {
            case "BOOL", "BOOLEAN" -> DataType.BOOLEAN;
            case "INT", "INTEGER" -> DataType.INTEGER;
            case "LONG" -> DataType.LONG;
            case "FLOAT" -> DataType.FLOAT;
            case "DOUBLE" -> DataType.DOUBLE;
            case "STRING", "TEXT", "BINARY" -> DataType.BINARY;
            default -> DataType.DOUBLE;
        };
    }

    public static Object parseValue(String rawValue, DataType dataType) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String trimmed = rawValue.trim();
        try {
            return switch (dataType) {
                case BOOLEAN -> parseBoolean(trimmed);
                case INTEGER -> Integer.parseInt(trimmed);
                case LONG -> Long.parseLong(trimmed);
                case FLOAT -> Float.parseFloat(trimmed);
                case DOUBLE -> Double.parseDouble(trimmed);
                case BINARY -> trimmed.getBytes(StandardCharsets.UTF_8);
            };
        } catch (Exception ex) {
            throw new IllegalArgumentException("数据类型转换失败: " + trimmed);
        }
    }

    private static Boolean parseBoolean(String value) {
        if ("1".equals(value)) {
            return true;
        }
        if ("0".equals(value)) {
            return false;
        }
        return Boolean.parseBoolean(value);
    }
}
