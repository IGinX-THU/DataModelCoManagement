package com.xmu.iginx.assoc.modules.sys.service.impl;

import cn.edu.tsinghua.iginx.session.SessionExecuteSqlResult;
import cn.edu.tsinghua.iginx.thrift.SqlType;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.util.TimeParser;
import com.xmu.iginx.assoc.modules.sys.dto.SqlExecuteRequest;
import com.xmu.iginx.assoc.modules.sys.service.SystemSqlService;
import com.xmu.iginx.assoc.modules.sys.vo.SqlExecuteResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SystemSqlServiceImpl implements SystemSqlService {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 1000;
    private static final String TITLE_DESCRIPTION = "title.description";

    private final IginxStorageWrapper iginxStorageWrapper;

    @Override
    public SqlExecuteResultVO execute(SqlExecuteRequest request) {
        String sql = request == null ? null : request.getSql();
        if (!StringUtils.hasText(sql)) {
            throw BizException.badRequest("SQL 不能为空");
        }
        long start = System.currentTimeMillis();
        SessionExecuteSqlResult result = iginxStorageWrapper.executeSql(sql.trim());
        long cost = System.currentTimeMillis() - start;

        String parseError = result.getParseErrorMsg();
        if (StringUtils.hasText(parseError)) {
            throw BizException.badRequest(parseError.trim());
        }

        SqlExecuteResultVO response = new SqlExecuteResultVO();
        response.setSqlType(result.getSqlType() == null ? "UNKNOWN" : result.getSqlType().name());
        response.setExecutionTimeMs(cost);

        int limit = normalizeLimit(request == null ? null : request.getLimit());
        boolean formatTime = request == null || request.getFormatTime() == null || request.getFormatTime();

        if (result.getSqlType() == SqlType.ShowColumns) {
            fillShowColumns(result, response, limit);
        } else if (result.getSqlType() == SqlType.GetReplicaNum) {
            fillSingleValue(response, "Replica num", result.getReplicaNum());
        } else if (result.getSqlType() == SqlType.CountPoints) {
            fillSingleValue(response, "Points num", result.getPointsNum());
        } else if (result.getConfigs() != null && !result.getConfigs().isEmpty()) {
            fillKeyValue(response, result.getConfigs(), limit);
        } else if (result.getSessionIDs() != null && !result.getSessionIDs().isEmpty()) {
            fillSessionIds(response, result.getSessionIDs(), limit);
        } else if (result.getRegisterTaskInfos() != null && !result.getRegisterTaskInfos().isEmpty()) {
            fillRegisterTasks(response, result.getRegisterTaskInfos(), limit);
        } else if (hasValues(result)) {
            fillQueryResult(result, response, limit, formatTime);
        } else {
            response.setMessage("执行成功");
            response.setColumns(List.of());
            response.setRows(List.of());
        }
        return response;
    }

    private void fillQueryResult(SessionExecuteSqlResult result,
                                 SqlExecuteResultVO response,
                                 int limit,
                                 boolean formatTime) {
        long[] keys = result.getKeys();
        List<String> rawPaths = result.getPaths() == null ? List.of() : result.getPaths();
        List<Integer> visibleIndex = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        if (keys != null) {
            columns.add("Time");
        }
        for (int i = 0; i < rawPaths.size(); i++) {
            String path = rawPaths.get(i);
            if (TITLE_DESCRIPTION.equals(path)) {
                continue;
            }
            visibleIndex.add(i);
            columns.add(path);
        }
        List<List<Object>> values = result.getValues() == null ? List.of() : result.getValues();
        List<Map<String, Object>> rows = new ArrayList<>();
        int rowCount = Math.min(values.size(), limit);
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            Map<String, Object> row = new LinkedHashMap<>();
            int colIndex = 0;
            if (keys != null && keys.length > rowIndex) {
                row.put(columns.get(colIndex++), formatKey(keys[rowIndex], formatTime));
            }
            List<Object> rowValues = values.get(rowIndex);
            for (int i = 0; i < visibleIndex.size(); i++) {
                int valueIndex = visibleIndex.get(i);
                Object value = valueIndex < rowValues.size() ? rowValues.get(valueIndex) : null;
                row.put(columns.get(colIndex + i), normalizeValue(value));
            }
            rows.add(row);
        }
        response.setColumns(columns);
        response.setRows(rows);
        if (rows.isEmpty()) {
            response.setMessage("查询成功，但没有数据");
        }
    }

    private void fillShowColumns(SessionExecuteSqlResult result, SqlExecuteResultVO response, int limit) {
        List<String> paths = result.getPaths() == null ? List.of() : result.getPaths();
        List<?> dataTypes = result.getDataTypeList() == null ? List.of() : result.getDataTypeList();
        List<String> columns = List.of("Path", "DataType");
        List<Map<String, Object>> rows = new ArrayList<>();
        int rowCount = Math.min(paths.size(), limit);
        for (int i = 0; i < rowCount; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(columns.get(0), paths.get(i));
            row.put(columns.get(1), i < dataTypes.size() ? String.valueOf(dataTypes.get(i)) : "");
            rows.add(row);
        }
        response.setColumns(columns);
        response.setRows(rows);
    }

    private void fillSingleValue(SqlExecuteResultVO response, String name, Object value) {
        response.setColumns(List.of(name));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(name, value);
        response.setRows(List.of(row));
    }

    private void fillKeyValue(SqlExecuteResultVO response, Map<String, String> configs, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int count = 0;
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            if (count++ >= limit) {
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Key", entry.getKey());
            row.put("Value", entry.getValue());
            rows.add(row);
        }
        response.setColumns(List.of("Key", "Value"));
        response.setRows(rows);
    }

    private void fillSessionIds(SqlExecuteResultVO response, List<Long> sessionIds, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int count = 0;
        for (Long id : sessionIds) {
            if (count++ >= limit) {
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("SessionId", id);
            rows.add(row);
        }
        response.setColumns(List.of("SessionId"));
        response.setRows(rows);
    }

    private void fillRegisterTasks(SqlExecuteResultVO response, List<?> tasks, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int count = 0;
        for (Object task : tasks) {
            if (count++ >= limit) {
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Task", String.valueOf(task));
            rows.add(row);
        }
        response.setColumns(List.of("Task"));
        response.setRows(rows);
    }

    private boolean hasValues(SessionExecuteSqlResult result) {
        if (result.getValues() != null && !result.getValues().isEmpty()) {
            return true;
        }
        if (result.getKeys() != null && result.getKeys().length > 0) {
            return true;
        }
        return result.getPaths() != null && !result.getPaths().isEmpty();
    }

    private Object normalizeValue(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value;
    }

    private Object formatKey(long key, boolean formatTime) {
        if (!formatTime) {
            return String.valueOf(key);
        }
        return TimeParser.formatMillis(TimeParser.toMillis(key));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
