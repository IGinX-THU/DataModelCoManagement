package com.xmu.iginx.assoc.modules.data.util;

import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.data.dto.StructuredQueryCondition;
import com.xmu.iginx.assoc.modules.data.util.JdbcValueConverter;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 结构化查询 SQL 构造器。
 */
public class StructuredSqlBuilder {

    /**
     * SQL 与参数封装。
     *
     * @param sql SQL 字符串
     * @param params 参数列表
     */
    public record SqlWithParams(String sql, List<Object> params) {
    }

    /**
     * 构建 WHERE 子句与参数列表。
     *
     * @param conditions 查询条件
     * @param allowedColumns 允许的列
     * @param columnTypes 列类型映射
     * @return SQL 与参数
     */
    public SqlWithParams buildWhereClause(List<StructuredQueryCondition> conditions,
                                          Set<String> allowedColumns,
                                          Map<String, Integer> columnTypes) {
        if (conditions == null || conditions.isEmpty()) {
            return new SqlWithParams("", List.of());
        }
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        boolean first = true;
        for (StructuredQueryCondition condition : conditions) {
            if (condition == null || condition.getField() == null || condition.getField().isBlank()) {
                continue;
            }
            String field = condition.getField().trim();
            if (allowedColumns != null && !allowedColumns.contains(field)) {
                continue;
            }
            Integer sqlType = columnTypes == null ? null : columnTypes.get(field);
            String op = normalizeOperator(condition.getOp());
            if (!first) {
                where.append(' ').append(normalizeLogic(condition.getLogic())).append(' ');
            }
            where.append(IginxStructuredUtils.quoteIdentifier(field)).append(' ').append(op).append(' ');
            // IN / NOT IN 均走列表参数拼接逻辑。
            if ("IN".equals(op) || "NOT IN".equals(op)) {
                List<String> values = splitValues(condition.getValue());
                if (values.isEmpty()) {
                    where.append("(NULL)");
                } else {
                    where.append('(');
                    for (int i = 0; i < values.size(); i++) {
                        if (i > 0) {
                            where.append(',');
                        }
                        where.append('?');
                        params.add(convertParam(field, values.get(i), sqlType));
                    }
                    where.append(')');
                }
            } else {
                where.append('?');
                params.add(convertParam(field, condition.getValue(), sqlType));
            }
            first = false;
        }
        return new SqlWithParams(first ? "" : " WHERE " + where, params);
    }

    /**
     * 构建 WHERE 子句（不带类型映射）。
     *
     * @param conditions 查询条件
     * @param allowedColumns 允许的列
     * @return SQL 与参数
     */
    public SqlWithParams buildWhereClause(List<StructuredQueryCondition> conditions, Set<String> allowedColumns) {
        return buildWhereClause(conditions, allowedColumns, null);
    }

    /**
     * 按字段类型转换参数值。
     *
     * @param field 字段名
     * @param value 原始值
     * @param sqlType JDBC 类型
     * @return 转换后的值
     */
    private Object convertParam(String field, String value, Integer sqlType) {
        if (sqlType == null) {
            return value;
        }
        try {
            return JdbcValueConverter.convert(value, sqlType);
        } catch (Exception ex) {
            throw BizException.badRequest("条件字段 " + field + " 值格式不正确");
        }
    }

    /**
     * 规范化比较运算符。
     *
     * @param op 原始运算符
     * @return 规范化运算符
     */
    private String normalizeOperator(String op) {
        if (op == null) {
            return "=";
        }
        String normalized = op.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "=", "!=", "<>", "<", ">", "<=", ">=", "LIKE", "IN", "NOT IN" -> normalized;
            default -> "=";
        };
    }

    /**
     * 规范化逻辑运算符。
     *
     * @param logic 原始逻辑
     * @return 规范化逻辑
     */
    private String normalizeLogic(String logic) {
        if (logic == null) {
            return "AND";
        }
        String normalized = logic.trim().toUpperCase(Locale.ROOT);
        return "OR".equals(normalized) ? "OR" : "AND";
    }

    /**
     * 拆分 IN 条件的值列表。
     *
     * @param raw 原始字符串
     * @return 值列表
     */
    private List<String> splitValues(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] parts = raw.split(",");
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }
}
