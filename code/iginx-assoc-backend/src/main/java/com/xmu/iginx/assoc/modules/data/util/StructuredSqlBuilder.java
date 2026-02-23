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

public class StructuredSqlBuilder {

    public record SqlWithParams(String sql, List<Object> params) {
    }

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
            if ("IN".equals(op)) {
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

    public SqlWithParams buildWhereClause(List<StructuredQueryCondition> conditions, Set<String> allowedColumns) {
        return buildWhereClause(conditions, allowedColumns, null);
    }

    private Object convertParam(String field, String value, Integer sqlType) {
        if (sqlType == null) {
            return value;
        }
        try {
            return JdbcValueConverter.convert(value, sqlType);
        } catch (Exception ex) {
            throw BizException.badRequest("鏉′欢瀛楁 " + field + " 鍊兼牸寮忎笉姝ｇ‘");
        }
    }

    private String normalizeOperator(String op) {
        if (op == null) {
            return "=";
        }
        String normalized = op.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "=", "!=", "<>", "<", ">", "<=", ">=", "LIKE", "IN" -> normalized;
            default -> "=";
        };
    }

    private String normalizeLogic(String logic) {
        if (logic == null) {
            return "AND";
        }
        String normalized = logic.trim().toUpperCase(Locale.ROOT);
        return "OR".equals(normalized) ? "OR" : "AND";
    }

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
