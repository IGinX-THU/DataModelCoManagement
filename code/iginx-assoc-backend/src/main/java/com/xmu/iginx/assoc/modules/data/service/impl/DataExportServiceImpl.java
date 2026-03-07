package com.xmu.iginx.assoc.modules.data.service.impl;

import cn.edu.tsinghua.iginx.session.QueryDataSet;
import cn.edu.tsinghua.iginx.session.SessionQueryDataSet;
import cn.edu.tsinghua.iginx.thrift.DataType;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.dto.DataExportRequest;
import com.xmu.iginx.assoc.modules.data.dto.StructuredQueryCondition;
import com.xmu.iginx.assoc.modules.data.entity.DataExportTaskEntity;
import com.xmu.iginx.assoc.modules.data.enums.DataExportTaskStatus;
import com.xmu.iginx.assoc.modules.data.enums.DataSourceType;
import com.xmu.iginx.assoc.modules.data.model.DataSourceDetail;
import com.xmu.iginx.assoc.modules.data.repository.DataExportTaskRepository;
import com.xmu.iginx.assoc.modules.data.service.DataExportService;
import com.xmu.iginx.assoc.modules.data.service.DataSourceAccessor;
import com.xmu.iginx.assoc.modules.data.util.CsvUtils;
import com.xmu.iginx.assoc.modules.data.util.DataFileStorageService;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredQueryHelper;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredUtils;
import com.xmu.iginx.assoc.modules.data.util.StructuredSqlBuilder;
import com.xmu.iginx.assoc.modules.data.util.TimeParser;
import com.xmu.iginx.assoc.modules.data.util.TimeSeriesPathUtils;
import com.xmu.iginx.assoc.modules.data.vo.DataExportResultVO;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataExportServiceImpl implements DataExportService {

    private static final long ASYNC_THRESHOLD_BYTES = 100L * 1024 * 1024;

    private final DataExportTaskRepository taskRepository;
    private final DataFileStorageService fileStorageService;
    private final DataSourceAccessor dataSourceAccessor;
    private final IginxStorageWrapper iginxStorageWrapper;
    private final IginxStructuredQueryHelper structuredQueryHelper;
    private final ObjectMapper objectMapper;
    private final StructuredSqlBuilder structuredSqlBuilder = new StructuredSqlBuilder();

    @Override
    public DataExportResultVO exportData(DataExportRequest request) {
        boolean async = Boolean.TRUE.equals(request.getAsync());
        if (request.getAsync() == null) {
            async = estimateAsync(request);
        }
        if (async) {
            DataExportTaskEntity task = createTask(request);
            runAsyncExport(task.getId(), request);
            return buildTaskResult(task);
        }
        DataFileStorageService.StoredFile file = performExport(request);
        DataExportResultVO result = new DataExportResultVO();
        result.setStatus(DataExportTaskStatus.SUCCESS.name());
        result.setFileName(file.fileName());
        result.setDownloadUrl("/api/v1/data/files/" + file.fileName());
        return result;
    }

    @Override
    public DataExportResultVO queryExportTask(Long taskId) {
        DataExportTaskEntity task = taskRepository.findById(taskId)
            .orElseThrow(() -> BizException.badRequest("导出任务不存在"));
        DataExportResultVO result = new DataExportResultVO();
        result.setTaskId(task.getId());
        result.setStatus(task.getStatus());
        result.setFileName(task.getFileName());
        if (task.getFileName() != null) {
            result.setDownloadUrl("/api/v1/data/files/" + task.getFileName());
        }
        return result;
    }

    @Async
    public void runAsyncExport(Long taskId, DataExportRequest request) {
        DataExportTaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        task.setStatus(DataExportTaskStatus.RUNNING.name());
        task.setUpdateTime(LocalDateTime.now());
        taskRepository.save(task);
        try {
            DataFileStorageService.StoredFile file = performExport(request);
            task.setFileName(file.fileName());
            task.setFilePath(file.path().toString());
            task.setStatus(DataExportTaskStatus.SUCCESS.name());
        } catch (BizException e) {
            throw e;
        } catch (Exception ex) {
            task.setStatus(DataExportTaskStatus.FAILED.name());
            task.setErrorMessage(ex.getMessage());
        }
        task.setUpdateTime(LocalDateTime.now());
        taskRepository.save(task);
    }

    private DataFileStorageService.StoredFile performExport(DataExportRequest request) {
        String type = request.getType().trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "TS", "TIME_SERIES", "TIMESERIES" -> exportTimeSeries(request);
            case "STRUCT", "STRUCTURED" -> exportStructured(request);
            default -> throw BizException.badRequest("不支持的导出类型");
        };
    }

    private DataFileStorageService.StoredFile exportTimeSeries(DataExportRequest request) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.INFLUXDB, DataSourceType.IOTDB);
        if (request.getPaths() == null || request.getPaths().isEmpty()) {
            throw BizException.badRequest("测点路径不能为空");
        }
        if (request.getTimeRange() == null) {
            throw BizException.badRequest("时间范围不能为空");
        }
        List<String> paths = TimeSeriesPathUtils.resolvePathsUnderMount(request.getPaths(), detail.entity().getMountPath(), true);
        long startNs = TimeParser.toNano(TimeParser.parseToMillis(request.getTimeRange().getStart(), null));
        long endNs = TimeParser.toNano(TimeParser.parseToMillis(request.getTimeRange().getEnd(), null));
        SessionQueryDataSet dataSet = iginxStorageWrapper.executeWithSession(session ->
            session.queryData(paths, startNs, endNs));
        String format = request.getFormat().trim().toUpperCase(Locale.ROOT);
        DataFileStorageService.StoredFile file = fileStorageService.createFile("export_ts", format.equals("JSON") ? ".json" : ".csv");
        if ("JSON".equals(format)) {
            writeTimeSeriesJson(file, dataSet);
        } else {
            writeTimeSeriesCsv(file, dataSet, request.getLayout());
        }
        return file;
    }

    private DataFileStorageService.StoredFile exportStructured(DataExportRequest request) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.POSTGRESQL);
        String format = request.getFormat().trim().toUpperCase(Locale.ROOT);
        DataFileStorageService.StoredFile file = fileStorageService.createFile("export_struct",
            format.equals("EXCEL") ? ".xlsx" : format.equals("JSON") ? ".json" : ".csv");
        try {
            Map<String, DataType> columnTypes = null;
            String schemaWithMount = IginxStructuredUtils.mergeMountPath(detail.entity().getMountPath(), request.getSchema());
            List<String> selectedColumns = normalizeExportColumns(request.getColumns());
            if (request.getSql() == null || request.getSql().isBlank()) {
                columnTypes = structuredQueryHelper.loadColumnTypes(schemaWithMount, request.getTable());
                validateExportColumns(selectedColumns, columnTypes);
            }
            StructuredSqlBuilder.SqlWithParams sqlWithParams = buildStructuredQuery(request, columnTypes, schemaWithMount);
            String finalSql = IginxStructuredUtils.renderSqlWithParams(sqlWithParams.sql(), sqlWithParams.params());
            QueryDataSet dataSet = structuredQueryHelper.executeQuery(finalSql, 1000);
            try {
                if ("EXCEL".equals(format)) {
                    writeStructuredExcel(file, dataSet, selectedColumns);
                } else if ("JSON".equals(format)) {
                    writeStructuredJson(file, dataSet, selectedColumns);
                } else {
                    writeStructuredCsv(file, dataSet, selectedColumns);
                }
            } finally {
                closeQuietly(dataSet);
            }
        } catch (Exception ex) {
            throw BizException.internal("结构化数据导出失败: " + ex.getMessage());
        }
        return file;
    }

    private void writeTimeSeriesCsv(DataFileStorageService.StoredFile file, SessionQueryDataSet dataSet, @Nullable String layout) {
        List<String> paths = dataSet.getPaths();
        long[] keys = dataSet.getKeys();
        List<List<Object>> values = dataSet.getValues();
        String mode = layout == null ? "wide" : layout.trim().toLowerCase(Locale.ROOT);
        try (BufferedWriter writer = Files.newBufferedWriter(file.path(), StandardCharsets.UTF_8)) {
            if ("long".equals(mode)) {
                writer.write("timestamp,path,value");
                writer.newLine();
                for (int i = 0; i < keys.length; i++) {
                    long millis = TimeParser.toMillis(keys[i]);
                    String ts = TimeParser.formatMillis(millis);
                    for (int j = 0; j < paths.size(); j++) {
                        writer.write(CsvUtils.toCsvValue(ts));
                        writer.write(",");
                        writer.write(CsvUtils.toCsvValue(paths.get(j)));
                        writer.write(",");
                        writer.write(CsvUtils.toCsvValue(values.get(i).get(j)));
                        writer.newLine();
                    }
                }
            } else {
                List<String> header = new ArrayList<>();
                header.add("timestamp");
                header.addAll(paths);
                writer.write(header.stream().map(CsvUtils::toCsvValue).collect(Collectors.joining(",")));
                writer.newLine();
                for (int i = 0; i < keys.length; i++) {
                    List<String> row = new ArrayList<>();
                    row.add(TimeParser.formatMillis(TimeParser.toMillis(keys[i])));
                    for (Object value : values.get(i)) {
                        if (value instanceof byte[] bytes) {
                            row.add(new String(bytes, StandardCharsets.UTF_8));
                        } else {
                            row.add(String.valueOf(value));
                        }
                    }
                    writer.write(row.stream().map(CsvUtils::toCsvValue).collect(Collectors.joining(",")));
                    writer.newLine();
                }
            }
        } catch (Exception ex) {
            throw BizException.internal("时序数据导出失败: " + ex.getMessage());
        }
    }

    private void writeTimeSeriesJson(DataFileStorageService.StoredFile file, SessionQueryDataSet dataSet) {
        try (OutputStream outputStream = Files.newOutputStream(file.path());
             JsonGenerator generator = objectMapper.getFactory().createGenerator(outputStream)) {
            generator.writeStartObject();
            generator.writeArrayFieldStart("timestamps");
            for (long key : dataSet.getKeys()) {
                generator.writeNumber(TimeParser.toMillis(key));
            }
            generator.writeEndArray();
            generator.writeArrayFieldStart("series");
            List<String> paths = dataSet.getPaths();
            List<List<Object>> values = dataSet.getValues();
            for (int i = 0; i < paths.size(); i++) {
                generator.writeStartObject();
                generator.writeStringField("path", paths.get(i));
                generator.writeArrayFieldStart("values");
                for (List<Object> row : values) {
                    generator.writeObject(row.get(i));
                }
                generator.writeEndArray();
                generator.writeEndObject();
            }
            generator.writeEndArray();
            generator.writeEndObject();
        } catch (Exception ex) {
            throw BizException.internal("时序数据导出失败: " + ex.getMessage());
        }
    }

    private void writeStructuredCsv(DataFileStorageService.StoredFile file,
                                    QueryDataSet dataSet,
                                    List<String> selectedColumns) throws Exception {
        StructuredExportMeta meta = resolveStructuredExportMeta(dataSet, selectedColumns);
        List<String> headers = meta.headers();
        List<Integer> indices = meta.indices();
        try (BufferedWriter writer = Files.newBufferedWriter(file.path(), StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            writer.write(headers.stream().map(CsvUtils::toCsvValue).collect(Collectors.joining(",")));
            writer.newLine();
            Object[] row;
            int columnCount = indices.size();
            while ((row = nextRowQuietly(dataSet)) != null) {
                if (isDeletedStructuredRow(row, meta.visibleDataIndices())) {
                    continue;
                }
                List<String> values = new ArrayList<>();
                for (int i = 0; i < columnCount; i++) {
                    int idx = indices.get(i);
                    Object value = idx < row.length ? row[idx] : null;
                    Object normalized = normalizeStructuredValue(value);
                    values.add(java.util.Objects.toString(normalized, ""));
                }
                writer.write(values.stream().map(CsvUtils::toCsvValue).collect(Collectors.joining(",")));
                writer.newLine();
            }
        }
    }

    private void writeStructuredExcel(DataFileStorageService.StoredFile file,
                                      QueryDataSet dataSet,
                                      List<String> selectedColumns) throws Exception {
        StructuredExportMeta meta = resolveStructuredExportMeta(dataSet, selectedColumns);
        List<String> headers = meta.headers();
        List<Integer> indices = meta.indices();
        List<List<String>> header = new ArrayList<>();
        for (String name : headers) {
            header.add(List.of(name));
        }
        try (ExcelWriter writer = EasyExcel.write(file.path().toFile()).head(header).build()) {
            WriteSheet sheet = EasyExcel.writerSheet("Sheet1").build();
            List<List<Object>> buffer = new ArrayList<>();
            Object[] row;
            int columnCount = indices.size();
            while ((row = nextRowQuietly(dataSet)) != null) {
                if (isDeletedStructuredRow(row, meta.visibleDataIndices())) {
                    continue;
                }
                List<Object> values = new ArrayList<>();
                for (int i = 0; i < columnCount; i++) {
                    int idx = indices.get(i);
                    Object value = idx < row.length ? row[idx] : null;
                    values.add(normalizeStructuredValue(value));
                }
                buffer.add(values);
                if (buffer.size() >= 2000) {
                    writer.write(buffer, sheet);
                    buffer.clear();
                }
            }
            if (!buffer.isEmpty()) {
                writer.write(buffer, sheet);
            }
        }
    }

    private void writeStructuredJson(DataFileStorageService.StoredFile file,
                                     QueryDataSet dataSet,
                                     List<String> selectedColumns) throws Exception {
        StructuredExportMeta meta = resolveStructuredExportMeta(dataSet, selectedColumns);
        List<String> headers = meta.headers();
        List<Integer> indices = meta.indices();
        try (OutputStream outputStream = Files.newOutputStream(file.path());
             JsonGenerator generator = objectMapper.getFactory().createGenerator(outputStream)) {
            generator.writeStartArray();
            Object[] row;
            int columnCount = indices.size();
            while ((row = nextRowQuietly(dataSet)) != null) {
                if (isDeletedStructuredRow(row, meta.visibleDataIndices())) {
                    continue;
                }
                generator.writeStartObject();
                for (int i = 0; i < columnCount; i++) {
                    int idx = indices.get(i);
                    Object value = idx < row.length ? row[idx] : null;
                    generator.writeObjectField(headers.get(i), normalizeStructuredValue(value));
                }
                generator.writeEndObject();
            }
            generator.writeEndArray();
        }
    }

    private Object normalizeStructuredValue(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value;
    }

    private boolean isDeletedStructuredRow(Object[] row, List<Integer> indices) {
        if (indices == null || indices.isEmpty()) {
            return false;
        }
        for (int idx : indices) {
            Object value = idx < row.length ? row[idx] : null;
            if (value != null) {
                return false;
            }
        }
        return true;
    }

    private Object[] nextRowQuietly(QueryDataSet dataSet) {
        try {
            return dataSet.nextRow();
        } catch (Exception ex) {
            return null;
        }
    }

    private StructuredExportMeta resolveStructuredExportMeta(QueryDataSet dataSet, List<String> selectedColumns) {
        List<String> headers = IginxStructuredUtils.normalizeStructuredHeaders(dataSet.getColumnList());
        List<String> visibleHeaders = new ArrayList<>();
        List<Integer> visibleIndices = new ArrayList<>();
        Map<String, Integer> exactIndexMap = new java.util.LinkedHashMap<>();
        Map<String, Integer> lowerIndexMap = new java.util.LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (IginxStructuredUtils.isInternalKey(header)) {
                continue;
            }
            visibleHeaders.add(header);
            visibleIndices.add(i);
            exactIndexMap.putIfAbsent(header, i);
            lowerIndexMap.putIfAbsent(header.toLowerCase(Locale.ROOT), i);
        }
        if (visibleHeaders.isEmpty()) {
            throw BizException.badRequest("Table columns not found");
        }
        if (selectedColumns == null || selectedColumns.isEmpty()) {
            return new StructuredExportMeta(visibleHeaders, visibleIndices, visibleIndices);
        }
        List<String> outputHeaders = new ArrayList<>();
        List<Integer> outputIndices = new ArrayList<>();
        for (String requested : selectedColumns) {
            Integer index = resolveColumnIndex(requested, exactIndexMap, lowerIndexMap);
            if (index == null) {
                throw BizException.badRequest("Export column not found: " + requested);
            }
            outputHeaders.add(headers.get(index));
            outputIndices.add(index);
        }
        if (outputHeaders.isEmpty()) {
            throw BizException.badRequest("No export columns selected");
        }
        return new StructuredExportMeta(outputHeaders, outputIndices, visibleIndices);
    }

    private Integer resolveColumnIndex(String column,
                                       Map<String, Integer> exactIndexMap,
                                       Map<String, Integer> lowerIndexMap) {
        if (column == null || column.isBlank()) {
            return null;
        }
        String trimmed = column.trim();
        Integer exact = exactIndexMap.get(trimmed);
        if (exact != null) {
            return exact;
        }
        return lowerIndexMap.get(trimmed.toLowerCase(Locale.ROOT));
    }

    private List<String> normalizeExportColumns(List<String> requestedColumns) {
        if (requestedColumns == null || requestedColumns.isEmpty()) {
            return List.of();
        }
        Set<String> deduplicated = new LinkedHashSet<>();
        for (String column : requestedColumns) {
            if (column == null || column.isBlank()) {
                continue;
            }
            String trimmed = column.trim();
            if (IginxStructuredUtils.isInternalKey(trimmed)) {
                continue;
            }
            deduplicated.add(trimmed);
        }
        if (deduplicated.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(deduplicated);
    }

    private void validateExportColumns(List<String> selectedColumns, Map<String, DataType> columnTypes) {
        if (selectedColumns == null || selectedColumns.isEmpty()) {
            return;
        }
        if (columnTypes == null || columnTypes.isEmpty()) {
            throw BizException.badRequest("Table columns not found");
        }
        Set<String> allowedLowerCase = columnTypes.keySet().stream()
            .filter(name -> name != null && !name.isBlank())
            .map(name -> name.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        for (String column : selectedColumns) {
            if (!allowedLowerCase.contains(column.toLowerCase(Locale.ROOT))) {
                throw BizException.badRequest("Export column not found: " + column);
            }
        }
    }

    private record StructuredExportMeta(List<String> headers, List<Integer> indices, List<Integer> visibleDataIndices) {
    }

    private StructuredSqlBuilder.SqlWithParams buildStructuredQuery(DataExportRequest request,
                                                                    Map<String, DataType> columnTypes,
                                                                    String schemaWithMount) {
        if (request.getSql() != null && !request.getSql().isBlank()) {
            String sql = request.getSql().trim();
            if (!sql.toUpperCase(Locale.ROOT).startsWith("SELECT")) {
                throw BizException.badRequest("仅支持 SELECT 语句导出");
            }
            return new StructuredSqlBuilder.SqlWithParams(sql, List.of());
        }
        String schema = request.getSchema();
        String table = request.getTable();
        if (schema == null || schema.isBlank() || table == null || table.isBlank()) {
            throw BizException.badRequest("结构化导出必须指定表");
        }
        if (columnTypes == null || columnTypes.isEmpty()) {
            throw BizException.badRequest("表结构不存在或无字段");
        }
        Map<String, Integer> sqlTypeMap = IginxStructuredUtils.mapIginxTypesToSqlTypes(columnTypes);
        sqlTypeMap = new java.util.LinkedHashMap<>(sqlTypeMap);
        sqlTypeMap.put(IginxStructuredUtils.INTERNAL_KEY, java.sql.Types.BIGINT);
        List<StructuredQueryCondition> conditions = request.getConditions();
        StructuredSqlBuilder.SqlWithParams where = structuredSqlBuilder.buildWhereClause(
            conditions, sqlTypeMap.keySet(), sqlTypeMap);
        String selectList = "*";
        String tablePath = IginxStructuredUtils.buildTablePath(schemaWithMount, table);
        String whereClause = appendKeyFilter(rewriteInternalKey(where.sql()));
        String sql = "SELECT " + selectList + " FROM " + tablePath + whereClause;
        return new StructuredSqlBuilder.SqlWithParams(sql, where.params());
    }

    private String appendKeyFilter(String whereSql) {
        String keyFilter = "KEY <> " + IginxStructuredUtils.DUMMY_KEY;
        if (whereSql == null || whereSql.isBlank()) {
            return " WHERE " + keyFilter;
        }
        return whereSql + " AND " + keyFilter;
    }

    private String rewriteInternalKey(String whereSql) {
        if (whereSql == null || whereSql.isBlank()) {
            return whereSql;
        }
        String normalized = whereSql;
        String quotedInternal = IginxStructuredUtils.quoteIdentifier(IginxStructuredUtils.INTERNAL_KEY);
        normalized = normalized.replace(quotedInternal, "KEY");
        normalized = normalized.replace(IginxStructuredUtils.INTERNAL_KEY, "KEY");
        return normalized;
    }

    private boolean estimateAsync(DataExportRequest request) {
        String type = request.getType().trim().toUpperCase(Locale.ROOT);
        if (type.startsWith("STRUCT")) {
            return estimateStructuredSize(request) > ASYNC_THRESHOLD_BYTES;
        }
        return false;
    }

    private long estimateStructuredSize(DataExportRequest request) {
        try {
            DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.POSTGRESQL);
            String schemaWithMount = IginxStructuredUtils.mergeMountPath(detail.entity().getMountPath(), request.getSchema());
            Map<String, DataType> columnTypes = structuredQueryHelper.loadColumnTypes(schemaWithMount, request.getTable());
            if (columnTypes == null || columnTypes.isEmpty()) {
                return 0;
            }
            Map<String, Integer> sqlTypeMap = IginxStructuredUtils.mapIginxTypesToSqlTypes(columnTypes);
            sqlTypeMap = new java.util.LinkedHashMap<>(sqlTypeMap);
            sqlTypeMap.put(IginxStructuredUtils.INTERNAL_KEY, java.sql.Types.BIGINT);
            StructuredSqlBuilder.SqlWithParams where = structuredSqlBuilder.buildWhereClause(
                request.getConditions(), sqlTypeMap.keySet(), sqlTypeMap);
            String tablePath = IginxStructuredUtils.buildTablePath(schemaWithMount, request.getTable());
            String whereClause = appendKeyFilter(rewriteInternalKey(where.sql()));
            String sql = "SELECT COUNT(*) FROM " + tablePath + whereClause;
            String finalSql = IginxStructuredUtils.renderSqlWithParams(sql, where.params());
            QueryDataSet dataSet = structuredQueryHelper.executeQuery(finalSql, 1000);
            try {
                Object[] row = dataSet.nextRow();
                long rows = 0L;
                if (row != null) {
                    for (Object value : row) {
                        if (value == null) {
                            continue;
                        }
                        if (value instanceof Number number) {
                            rows = Math.max(rows, number.longValue());
                        } else {
                            try {
                                rows = Math.max(rows, Long.parseLong(String.valueOf(value)));
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
                int columns = Math.max(1, columnTypes.size());
                List<String> selectedColumns = normalizeExportColumns(request.getColumns());
                if (!selectedColumns.isEmpty()) {
                    columns = selectedColumns.size();
                }
                return rows * columns * 16L;
            } finally {
                closeQuietly(dataSet);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private void closeQuietly(QueryDataSet dataSet) {
        if (dataSet == null) {
            return;
        }
        try {
            dataSet.close();
        } catch (Exception ignored) {
        }
    }

    private DataExportTaskEntity createTask(DataExportRequest request) {
        DataExportTaskEntity task = new DataExportTaskEntity();
        task.setSourceId(request.getSourceId());
        task.setExportType(request.getType());
        task.setFormat(request.getFormat());
        task.setStatus(DataExportTaskStatus.PENDING.name());
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        try {
            task.setRequestJson(objectMapper.writeValueAsString(request));
        } catch (Exception ignored) {
        }
        return taskRepository.save(task);
    }

    private DataExportResultVO buildTaskResult(DataExportTaskEntity task) {
        DataExportResultVO result = new DataExportResultVO();
        result.setTaskId(task.getId());
        result.setStatus(task.getStatus());
        return result;
    }
}





