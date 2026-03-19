package com.xmu.iginx.assoc.modules.data.service.impl;

import cn.edu.tsinghua.iginx.session.Column;
import cn.edu.tsinghua.iginx.session.QueryDataSet;
import cn.edu.tsinghua.iginx.thrift.DataType;
import com.alibaba.excel.EasyExcel;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.dto.StructuredImportRequest;
import com.xmu.iginx.assoc.modules.data.dto.TimeSeriesColumnMappingDTO;
import com.xmu.iginx.assoc.modules.data.dto.TimeSeriesImportRequest;
import com.xmu.iginx.assoc.modules.data.service.DataImportService;
import com.xmu.iginx.assoc.modules.data.util.CsvUtils;
import com.xmu.iginx.assoc.modules.data.util.DataPrefixRules;
import com.xmu.iginx.assoc.modules.data.util.DataFileStorageService;
import com.xmu.iginx.assoc.modules.data.util.ExcelRowListener;
import com.xmu.iginx.assoc.modules.data.util.IginxDataTypeConverter;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredQueryHelper;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredUtils;
import com.xmu.iginx.assoc.modules.data.util.StructuredKeyGenerator;
import com.xmu.iginx.assoc.modules.data.util.TimeParser;
import com.xmu.iginx.assoc.modules.data.util.TimeSeriesPathUtils;
import com.xmu.iginx.assoc.modules.data.vo.DataImportResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据导入服务实现，支持时序与结构化数据导入。
 */
@Service
@RequiredArgsConstructor
public class DataImportServiceImpl implements DataImportService {

    private static final int TS_BATCH_SIZE = 10000;
    private static final int STRUCT_BATCH_SIZE = 2000;
    private static final String BOM = "\uFEFF";

    private final IginxStorageWrapper iginxStorageWrapper;
    private final DataFileStorageService fileStorageService;
    private final IginxStructuredQueryHelper structuredQueryHelper;

    /**
     * 导入时序数据（CSV/Excel）。
     * @param request 导入请求参数
     * @param file 上传文件
     * @return 导入结果
     */
    @Override
    public DataImportResultVO importTimeSeries(TimeSeriesImportRequest request, MultipartFile file) {
        String storageGroup = normalizeTimeSeriesImportPath(request.getStorageGroup());
        String extension = getExtension(file);
        // 根据文件类型选择解析器，进入统一的导入上下文
        TimeSeriesImportContext context = new TimeSeriesImportContext(storageGroup, request);
        if ("csv".equals(extension)) {
            readCsv(file, context::handleRow);
        } else if ("xlsx".equals(extension) || "xls".equals(extension)) {
            readExcel(file, 0, context::handleRow);
        } else {
            throw BizException.badRequest("仅支持 CSV 或 Excel 文件");
        }
        context.flush();
        return context.buildResult();
    }

    /**
     * 导入结构化数据（CSV/Excel/SQL）。
     * @param request 导入请求参数
     * @param file 上传文件
     * @return 导入结果
     */
    @Override
    public DataImportResultVO importStructured(StructuredImportRequest request, MultipartFile file) {
        StructuredImportPath targetPath = normalizeStructuredImportPath(request);
        String extension = resolveFileType(request.getFileType(), file);
        if ("sql".equals(extension)) {
            return importStructuredSql(file);
        }
        if (!List.of("csv", "xlsx", "xls").contains(extension)) {
            throw BizException.badRequest("仅支持 CSV、Excel 或 SQL 文件");
        }
        try {
            StructuredImportContext context = new StructuredImportContext(request, targetPath);
            if ("csv".equals(extension)) {
                readCsv(file, context::handleRow);
            } else {
                readExcel(file, Optional.ofNullable(request.getSheetIndex()).orElse(0), context::handleRow);
            }
            context.flush();
            return context.buildResult();
        } catch (BizException e) {
            throw e;
        } catch (Exception ex) {
            throw BizException.internal("结构化导入失败: " + ex.getMessage());
        }
    }

    /**
     * 导入 SQL 脚本中的结构化数据。
     * @param file SQL 文件
     * @return 导入结果
     */
    private DataImportResultVO importStructuredSql(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder buffer = new StringBuilder();
            String line;
            long total = 0;
            while ((line = reader.readLine()) != null) {
                buffer.append(line).append('\n');
                if (line.trim().endsWith(";")) {
                    String sql = buffer.toString().trim();
                    if (!sql.isBlank()) {
                        structuredQueryHelper.executeSql(sql);
                        total++;
                    }
                    buffer.setLength(0);
                }
            }
            if (!buffer.isEmpty()) {
                String sql = buffer.toString().trim();
                if (!sql.isBlank()) {
                    structuredQueryHelper.executeSql(sql);
                    total++;
                }
            }
            DataImportResultVO result = new DataImportResultVO();
            result.setTotal(total);
            result.setSuccess(total);
            result.setFailed(0);
            return result;
        } catch (Exception ex) {
            throw BizException.internal("SQL 导入失败: " + ex.getMessage());
        }
    }
    /**
     * 读取 CSV 文件并逐行交给消费函数处理。
     * @param file 上传文件
     * @param consumer 行消费函数     */
    private void readCsv(MultipartFile file, RowConsumer consumer) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                List<String> values = CsvUtils.parseLine(line);
                consumer.accept(values, isHeader);
                isHeader = false;
            }
        } catch (Exception ex) {
            throw BizException.internal("读取 CSV 文件失败: " + ex.getMessage());
        }
    }

    /**
     * 读取 Excel 文件并逐行交给消费函数处理。
     * @param file 上传文件
     * @param sheetIndex 工作表索引
     * * @param consumer 行消费函数     */
    private void readExcel(MultipartFile file, Integer sheetIndex, RowConsumer consumer) {
        try {
            int index = Optional.ofNullable(sheetIndex).orElse(0);
            EasyExcel.read(file.getInputStream(), new ExcelRowListener((row, header) -> consumer.accept(row, header)))
                .sheet(index)
                .doRead();
        } catch (Exception ex) {
            throw BizException.internal("读取 Excel 文件失败: " + ex.getMessage());
        }
    }

    /**
     * 获取文件扩展名（小写）。
     * @param file 上传文件
     * @return 扩展名     */
    private String getExtension(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 解析最终文件类型，优先使用请求指定类型。
     * @param fileType 请求声明类型
     * @param file 上传文件
     * @return 文件类型
     */
    private String resolveFileType(String fileType, MultipartFile file) {
        if (fileType != null && !fileType.isBlank()) {
            return fileType.trim().toLowerCase(Locale.ROOT);
        }
        return getExtension(file);
    }

    /**
     * 规整并校验时序导入路径，确保以 ts 前缀开头。
     * @param rawPath 原始路径
     * @return 归一化后的路径     */
    private String normalizeTimeSeriesImportPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw BizException.badRequest("导入路径不能为空");
        }
        if (!DataPrefixRules.startsWithPrefix(rawPath, DataPrefixRules.TS_PREFIX)) {
            throw BizException.badRequest("时序数据导入路径必须以 ts 开头");
        }
        return rawPath;
    }

    /**
     * 规整并校验结构化导入路径，确保 schema 以 rt 前缀开头。
     * @param request 导入请求
     */
    private StructuredImportPath normalizeStructuredImportPath(StructuredImportRequest request) {
        if (request == null) {
            throw BizException.badRequest("导入参数不能为空");
        }
        String rawPath = request.getTargetPath();
        if (rawPath == null || rawPath.isBlank()) {
            throw BizException.badRequest("导入目标路径不能为空");
        }
        if (!DataPrefixRules.startsWithPrefix(rawPath, DataPrefixRules.RT_PREFIX)) {
            throw BizException.badRequest("结构化数据导入路径必须以 rt 开头");
        }
        List<String> segments = IginxStructuredUtils.splitPathSegments(rawPath);
        if (segments.size() < 2) {
            throw BizException.badRequest("结构化导入路径格式不正确，至少包含 schema.table");
        }
        String table = segments.get(segments.size() - 1);
        String schema = String.join(".", segments.subList(0, segments.size() - 1));
        if (schema.isBlank() || table.isBlank()) {
            throw BizException.badRequest("结构化导入路径格式不正确，缺少 schema 或 table");
        }
        request.setTargetPath(rawPath);
        return new StructuredImportPath(schema, table);
    }

    private static class StructuredImportPath {
        private final String schema;
        private final String table;

        private StructuredImportPath(String schema, String table) {
            this.schema = schema;
            this.table = table;
        }
    }

    /**
     * 行数据消费函数。
     */
    @FunctionalInterface
    private interface RowConsumer {
        /**
         * 处理一行数据。
         * @param row 行数据
         * * @param header 是否表头
         */
        void accept(List<String> row, boolean header);
    }

    /**
     * 时序数据导入上下文，负责缓存行数据并批量写入。
     */
    private class TimeSeriesImportContext {
        private final String storageGroup;
        private final TimeSeriesImportRequest request;
        private final List<String> paths = new ArrayList<>();
        private final List<String> mappingColumns = new ArrayList<>();
        private final List<DataType> dataTypes = new ArrayList<>();
        private final List<Boolean> explicitTypes = new ArrayList<>();
        private final List<Long> keys = new ArrayList<>();
        private final List<List<Object>> values = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final Map<String, Integer> columnIndex = new HashMap<>();
        private boolean headerReady = false;
        private long total = 0;
        private long success = 0;
        private long failed = 0;

        private TimeSeriesImportContext(String storageGroup, TimeSeriesImportRequest request) {
            this.storageGroup = storageGroup;
            this.request = request;
        }

        /**
         * 处理 CSV/Excel 的单行数据。
         * @param row 行数据
         * * @param header 是否表头
         */
        private void handleRow(List<String> row, boolean header) {
            if (header) {
                buildHeader(row);
                return;
            }
            if (!headerReady) {
                throw BizException.badRequest("时间序列导入缺少表头");
            }
            total++;
            try {
                long timestamp = parseTimestamp(row);
                List<Object> rowValues = new ArrayList<>(mappingColumns.size());
                for (int i = 0; i < mappingColumns.size(); i++) {
                    String column = mappingColumns.get(i);
                    Integer idx = columnIndex.get(column);
                    String rawValue = idx == null || idx >= row.size() ? null : row.get(idx);
                    Object value = IginxDataTypeConverter.parseValue(rawValue, dataTypes.get(i));
                    rowValues.add(value);
                }
                keys.add(timestamp);
                for (int i = 0; i < rowValues.size(); i++) {
                    values.get(i).add(rowValues.get(i));
                }
                success++;
                if (keys.size() >= TS_BATCH_SIZE) {
                    flush();
                }
            } catch (Exception ex) {
                failed++;
                errors.add(ex.getMessage());
            }
        }

        /**
         * 解析表头并构建测点映射与类型配置。
         * @param header 表头行         */
        private void buildHeader(List<String> header) {
            columnIndex.clear();
            for (int i = 0; i < header.size(); i++) {
                String name = header.get(i);
                if (i == 0 && name != null && name.startsWith(BOM)) {
                    name = name.replace(BOM, "");
                }
                columnIndex.put(name.trim(), i);
            }
            String timestampColumn = request.getTimestampColumn();
            if (timestampColumn == null || !columnIndex.containsKey(timestampColumn)) {
                throw BizException.badRequest("时间戳列不存在或未配置");
            }
            List<TimeSeriesColumnMappingDTO> mappings = request.getMappings();
            if (mappings == null || mappings.isEmpty()) {
                // 未指定映射时，默认使用除时间戳外的列作为测点
                mappings = header.stream()
                    .filter(col -> !col.equals(timestampColumn))
                    .map(col -> {
                        TimeSeriesColumnMappingDTO dto = new TimeSeriesColumnMappingDTO();
                        dto.setColumn(col);
                        dto.setTarget(storageGroup + "." + col);
                        return dto;
                    })
                    .collect(Collectors.toList());
            }
            for (TimeSeriesColumnMappingDTO mapping : mappings) {
                if (mapping.getColumn() == null || mapping.getColumn().isBlank()) {
                    continue;
                }
                if (!columnIndex.containsKey(mapping.getColumn())) {
                    throw BizException.badRequest("列不存在: " + mapping.getColumn());
                }
                String target = mapping.getTarget();
                if (target == null || target.isBlank()) {
                    throw BizException.badRequest("目标测点不能为空");
                }
                // 统一目标测点路径，避免路径不一致
                String normalizedTarget = target.trim();
                if (normalizedTarget.isBlank()) {
                    throw BizException.badRequest("閻╊喗鐖ｅù瀣仯娑撳秷鍏樻稉铏光敄");
                }
                if (!TimeSeriesPathUtils.startsWithPath(normalizedTarget, storageGroup)) {
                    if (!DataPrefixRules.startsWithPrefix(normalizedTarget, DataPrefixRules.TS_PREFIX)) {
                        normalizedTarget = TimeSeriesPathUtils.joinPath(storageGroup, normalizedTarget);
                    }
                }
                paths.add(normalizedTarget);
                mappingColumns.add(mapping.getColumn());
                String rawType = mapping.getDataType();
                boolean hasExplicitType = rawType != null && !rawType.isBlank();
                dataTypes.add(IginxDataTypeConverter.parseType(rawType));
                explicitTypes.add(hasExplicitType);
                values.add(new ArrayList<>());
            }
            if (paths.isEmpty()) {
                throw BizException.badRequest("未解析到可导入的测点列，请检查表头或映射配置");
            }
            // 对齐已有测点的数据类型，避免重复写入导致类型冲突
            alignDataTypesWithExisting();
            headerReady = true;
        }

        /**
         * 与已存在测点的数据类型对齐，避免类型冲突。
         */
        private void alignDataTypesWithExisting() {
            List<Column> columns = iginxStorageWrapper.executeWithSession(session -> session.showColumns());
            if (columns == null || columns.isEmpty()) {
                return;
            }
            Map<String, DataType> existingTypes = new HashMap<>();
            for (Column column : columns) {
                if (column == null || column.getPath() == null) {
                    continue;
                }
                existingTypes.put(column.getPath(), column.getDataType());
            }
            for (int i = 0; i < paths.size(); i++) {
                String path = paths.get(i);
                DataType existingType = existingTypes.get(path);
                if (existingType == null) {
                    continue;
                }
                DataType requestedType = dataTypes.get(i);
                // 显式指定类型时，若与已存在类型冲突则直接报错
                if (explicitTypes.get(i)) {
                    if (!existingType.equals(requestedType)) {
                        throw BizException.badRequest("测点数据类型不一致，路径: " + path + "，已存在类型: " + existingType + "，请求类型: " + requestedType);
                    }
                } else {
                    dataTypes.set(i, existingType);
                }
            }
        }

        /**
         * 解析时间戳列并转换为纳秒。
         * @param row 行数据
         * * @return 纳秒时间戳         */
        private long parseTimestamp(List<String> row) {
            Integer idx = columnIndex.get(request.getTimestampColumn());
            if (idx == null || idx >= row.size()) {
                throw new IllegalArgumentException("时间戳列缺失");
            }
            String value = row.get(idx);
            return TimeParser.toNano(TimeParser.parseToMillis(value, request.getTimestampFormat()));
        }

        /**
         * 批量写入缓存数据并清理内存。
         */
        /**
         * 批量写入结构化数据并处理冲突策略。
         */
        private void flush() {
            if (keys.isEmpty()) {
                return;
            }
            // 批量写入时序数据，按列写入提升效率
            long[] keyArray = keys.stream().mapToLong(Long::longValue).toArray();
            Object[] valuesArray = new Object[values.size()];
            for (int i = 0; i < values.size(); i++) {
                valuesArray[i] = values.get(i).toArray();
            }
            iginxStorageWrapper.executeWithSession(session -> {
                session.insertColumnRecords(paths, keyArray, valuesArray, dataTypes);
                return null;
            });
            keys.clear();
            values.forEach(List::clear);
        }

        /**
         * 构建导入结果。
         * @return 导入结果
         */
        private DataImportResultVO buildResult() {
            DataImportResultVO result = new DataImportResultVO();
            result.setTotal(total);
            result.setSuccess(success);
            result.setFailed(failed);
            return result;
        }
    }

    /**
     * 结构化批量写入行。
     */
    private static class StructuredBatchRow {
        private final long key;
        private final Object[] values;
        private final List<String> rawRow;

        private StructuredBatchRow(long key, Object[] values, List<String> rawRow) {
            this.key = key;
            this.values = values;
            this.rawRow = rawRow;
        }
    }

    /**
     * 结构化数据导入上下文，负责解析表头、批量写入与错误记录。
     */
    private class StructuredImportContext {
        private static final int KEY_QUERY_BATCH = 500;

        private final StructuredImportRequest request;
        private final String schema;
        private final String table;
        private final String schemaPath;
        private final List<String> columns = new ArrayList<>();
        private final Map<String, Integer> columnIndex = new HashMap<>();
        private final List<StructuredBatchRow> batchRows = new ArrayList<>();
        private final List<String> errorMessages = new ArrayList<>();
        private final List<List<String>> errorRows = new ArrayList<>();
        private final Map<String, DataType> columnTypes = new LinkedHashMap<>();
        private List<String> primaryKeys = new ArrayList<>();
        private Integer internalKeyIndex = null;
        private boolean headerReady = false;
        private long total = 0;
        private long success = 0;
        private long failed = 0;
        private final String conflictStrategy;

        private StructuredImportContext(StructuredImportRequest request, StructuredImportPath targetPath) {
            this.request = request;
            this.schema = targetPath.schema;
            this.table = targetPath.table;
            this.schemaPath = DataPrefixRules.normalizeStructuredSchema(this.schema);
            this.conflictStrategy = Optional.ofNullable(request.getConflictStrategy())
                .orElse("update")
                .trim()
                .toLowerCase(Locale.ROOT);
        }

        /**
         * 处理结构化数据的单行内容。
         * @param row 行数据
         * * @param header 是否表头
         */
        private void handleRow(List<String> row, boolean header) {
            if (header) {
                buildHeader(row);
                return;
            }
            if (!headerReady) {
                throw BizException.badRequest("结构化导入缺少表头");
            }
            total++;
            try {
                StructuredBatchRow batchRow = buildBatchRow(row);
                batchRows.add(batchRow);
                if (batchRows.size() >= STRUCT_BATCH_SIZE) {
                    flush();
                }
            } catch (Exception ex) {
                failed++;
                errorMessages.add(ex.getMessage());
                errorRows.add(normalizeErrorRow(row));
            }
        }

        /**
         * 解析表头并准备列与主键信息。
         * @param header 表头行         */
        private void buildHeader(List<String> header) {
            columns.clear();
            columnIndex.clear();
            internalKeyIndex = null;
            for (int i = 0; i < header.size(); i++) {
                String name = header.get(i);
                if (i == 0 && name != null && name.startsWith(BOM)) {
                    name = name.replace(BOM, "");
                }
                if (name == null || name.isBlank()) {
                    continue;
                }
                String trimmed = name.trim();
                columnIndex.put(trimmed, i);
                if (IginxStructuredUtils.isInternalKey(trimmed)) {
                    internalKeyIndex = i;
                    continue;
                }
                columns.add(trimmed);
            }
            if (columns.isEmpty()) {
                throw BizException.badRequest("CSV/Excel 表头为空");
            }
            if (request.getPrimaryKeys() != null && !request.getPrimaryKeys().isEmpty()) {
                primaryKeys = request.getPrimaryKeys();
                for (String key : primaryKeys) {
                    if (!columnIndex.containsKey(key)) {
                        throw BizException.badRequest("主键字段不存在: " + key);
                    }
                }
            } else {
                primaryKeys = List.of();
            }
            prepareColumnTypes();
            headerReady = true;
        }

        /**
         * 初始化列类型，必要时触发自动建表。
         */
        private void prepareColumnTypes() {
            Map<String, DataType> existing = structuredQueryHelper.loadColumnTypes(schemaPath, table);
            boolean tableExists = existing != null && !existing.isEmpty();
            if (!tableExists) {
                if (request.isAutoCreateTable()) {
                    throw BizException.badRequest("自动建表已禁用，请先建表");
                }
                throw BizException.badRequest("目标表不存在，请先建表");
            }
            for (String column : columns) {
                DataType type = existing == null ? null : existing.get(column);
                if (type == null) {
                    type = DataType.BINARY;
                }
                columnTypes.put(column, type);
            }
        }

        /**
         * 构建批量写入行对象。
         *
         * @param row 原始行数据
         * @return 批量行对象
         */
        private StructuredBatchRow buildBatchRow(List<String> row) {
            long key = resolveRowKey(row);
            Object[] values = convertRow(row);
            return new StructuredBatchRow(key, values, new ArrayList<>(row));
        }

        /**
         * 解析行主键（内部键优先，其次主键字段）。
         * @param row 行数据
         * * @return 行主键         */
        private long resolveRowKey(List<String> row) {
            Long internal = extractInternalKey(row);
            if (internal != null) {
                if (IginxStructuredUtils.isReservedKey(internal)) {
                    throw BizException.badRequest("内部键 _iginx_key 不合法");
                }
                if (internal < 0) {
                    throw BizException.badRequest("内部键 _iginx_key 不能为负数");
                }
                return internal;
            }
            // 未提供内部键时，优先使用主键字段；否则生成随机键
            if (!primaryKeys.isEmpty()) {
                Map<String, Object> keyFields = new LinkedHashMap<>();
                for (String key : primaryKeys) {
                    Integer idx = columnIndex.get(key);
                    String raw = idx == null || idx >= row.size() ? null : row.get(idx);
                    String normalized = normalizeCell(raw, idx);
                    if (normalized == null || normalized.isBlank()) {
                        throw BizException.badRequest("主键字段不能为空: " + key);
                    }
                    keyFields.put(key, normalized);
                }
                return StructuredKeyGenerator.hashKey(keyFields);
            }
            return StructuredKeyGenerator.randomKey();
        }

        /**
         * 提取内部键 _iginx_key。
         * @param row 行数据
         * * @return 内部键；不存在则返回 null
         */
        private Long extractInternalKey(List<String> row) {
            if (internalKeyIndex == null || internalKeyIndex < 0) {
                return null;
            }
            if (internalKeyIndex >= row.size()) {
                return null;
            }
            String raw = normalizeCell(row.get(internalKeyIndex), internalKeyIndex);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                long value = Long.parseLong(raw.trim());
                if (value < 0) {
                    throw BizException.badRequest("内部键 _iginx_key 不能为负数");
                }
                if (IginxStructuredUtils.isReservedKey(value)) {
                    throw BizException.badRequest("内部键 _iginx_key 不合法");
                }
                return value;
            } catch (Exception ex) {
                throw BizException.badRequest("内部键 _iginx_key 不合法");
            }
        }

        /**
         * 将行数据按列类型转换为对象数组。
         * @param row 行数据
         * * @return 转换后的值数组         */
        private Object[] convertRow(List<String> row) {
            Object[] values = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                String column = columns.get(i);
                Integer idx = columnIndex.get(column);
                String raw = idx == null || idx >= row.size() ? null : row.get(idx);
                raw = normalizeCell(raw, idx);
                DataType type = columnTypes.getOrDefault(column, DataType.BINARY);
                values[i] = convertValue(raw, type);
            }
            return values;
        }

        /**
         * 规范化单元格内容，处理 BOM 等特殊情况。
         * @param raw 鍘熷鍐呭
         * @param index 列索引
         * * @return 规范化后的内容         */
        private String normalizeCell(String raw, Integer index) {
            if (raw == null) {
                return null;
            }
            String value = raw;
            if (index != null && index == 0 && value.startsWith(BOM)) {
                value = value.replace(BOM, "");
            }
            return value;
        }

        /**
         * 按列类型转换单元格值。
         * @param raw 原始字符
         * * @param type 列类型         * @return 转换后的值         */
        private Object convertValue(String raw, DataType type) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            if (type == DataType.BINARY) {
                return raw.getBytes(StandardCharsets.UTF_8);
            }
            return IginxDataTypeConverter.parseValue(raw, type);
        }

        private void flush() {
            if (batchRows.isEmpty()) {
                return;
            }
            List<StructuredBatchRow> rows = new ArrayList<>(batchRows);
            batchRows.clear();
            // ignore 策略需要查询已有主键并过滤重复记录
            Set<Long> existingKeys = queryExistingKeysIfNeeded(rows);
            List<StructuredBatchRow> rowsToInsert = filterRowsForInsert(rows, existingKeys);
            List<StructuredBatchRow> deduplicated = deduplicateRows(rowsToInsert);
            try {
                if (!deduplicated.isEmpty()) {
                    executeBatchInsert(deduplicated);
                }
                success += rows.size();
            } catch (Exception ex) {
                handleBatchFailure(rows, existingKeys);
            }
        }

        /**
         * 按冲突策略决定是否查询已存在主键。
         * @param rows 批量行
         * * @return 已存在主键集合         */
        private Set<Long> queryExistingKeysIfNeeded(List<StructuredBatchRow> rows) {
            if (!"ignore".equals(conflictStrategy)) {
                return Set.of();
            }
            Set<Long> keys = new HashSet<>();
            for (StructuredBatchRow row : rows) {
                if (row.key != IginxStructuredUtils.DUMMY_KEY) {
                    keys.add(row.key);
                }
            }
            if (keys.isEmpty()) {
                return Set.of();
            }
            return queryExistingKeys(new ArrayList<>(keys));
        }

        /**
         * 批量查询已存在主键。
         * @param keys 主键列表
         * @return 已存在主键集合         */
        private Set<Long> queryExistingKeys(List<Long> keys) {
            Set<Long> existing = new HashSet<>();
            String tablePath = IginxStructuredUtils.buildTablePath(schemaPath, table);
            int index = 0;
            while (index < keys.size()) {
                int end = Math.min(index + KEY_QUERY_BATCH, keys.size());
                List<Long> batch = keys.subList(index, end);
                String inClause = batch.stream().map(String::valueOf).collect(Collectors.joining(", "));
                String sql = "SELECT KEY FROM " + tablePath + " WHERE KEY IN (" + inClause + ")";
                QueryDataSet dataSet = structuredQueryHelper.executeQuery(sql, batch.size());
                try {
                    Object[] row;
                    while ((row = nextRowQuietly(dataSet)) != null) {
                        Long value = parseLong(row[0]);
                        if (value != null) {
                            existing.add(value);
                        }
                    }
                } finally {
                    closeQuietly(dataSet);
                }
                index = end;
            }
            return existing;
        }

        /**
         * 安全解析 Long 类型。
         * @param value 原始值
         * * @return Long 值         */
        private Long parseLong(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (Exception ex) {
                return null;
            }
        }

        /**
         * 根据冲突策略过滤可插入的行。
         * @param rows 批量行
         * * @param existingKeys 已存在主键         * @return 过滤后的行         */
        private List<StructuredBatchRow> filterRowsForInsert(List<StructuredBatchRow> rows, Set<Long> existingKeys) {
            if (!"ignore".equals(conflictStrategy) || existingKeys.isEmpty()) {
                return rows;
            }
            List<StructuredBatchRow> filtered = new ArrayList<>();
            for (StructuredBatchRow row : rows) {
                if (!existingKeys.contains(row.key)) {
                    filtered.add(row);
                }
            }
            return filtered;
        }

        /**
         * 对批量行做去重处理。
         * @param rows 批量行
         * * @return 去重后的行         */
        private List<StructuredBatchRow> deduplicateRows(List<StructuredBatchRow> rows) {
            if (rows.size() <= 1) {
                return rows;
            }
            Map<Long, StructuredBatchRow> unique = new LinkedHashMap<>();
            for (StructuredBatchRow row : rows) {
                if ("ignore".equals(conflictStrategy)) {
                    unique.putIfAbsent(row.key, row);
                } else {
                    unique.put(row.key, row);
                }
            }
            return new ArrayList<>(unique.values());
        }

        /**
         * 执行批量插入 SQL。
         * @param rows 批量行         */
        private void executeBatchInsert(List<StructuredBatchRow> rows) {
            String sql = buildInsertSql(rows);
            structuredQueryHelper.executeSql(sql);
        }

        /**
         * 构建批量插入 SQL。
         * @param rows 批量行
         * * @return SQL 字符串         */
        private String buildInsertSql(List<StructuredBatchRow> rows) {
            String tablePath = IginxStructuredUtils.buildTablePath(schemaPath, table);
            StringBuilder builder = new StringBuilder();
            builder.append("INSERT INTO ").append(tablePath).append(" (KEY");
            for (String column : columns) {
                builder.append(", ").append(IginxStructuredUtils.buildInsertColumn(column));
            }
            builder.append(") VALUES ");
            for (int i = 0; i < rows.size(); i++) {
                StructuredBatchRow row = rows.get(i);
                builder.append("(").append(row.key);
                for (Object value : row.values) {
                    builder.append(", ").append(IginxStructuredUtils.toSqlLiteral(value));
                }
                builder.append(")");
                if (i < rows.size() - 1) {
                    builder.append(", ");
                }
            }
            return builder.toString();
        }

        /**
         * 批量插入失败时逐行回退处理。
         * @param rows 批量行
         * * @param existingKeys 已存在主键         */
        private void handleBatchFailure(List<StructuredBatchRow> rows, Set<Long> existingKeys) {
            for (StructuredBatchRow row : rows) {
                if ("ignore".equals(conflictStrategy) && existingKeys.contains(row.key)) {
                    success++;
                    continue;
                }
                try {
                    executeBatchInsert(List.of(row));
                    success++;
                } catch (Exception ex) {
                    failed++;
                    errorMessages.add(ex.getMessage());
                    errorRows.add(normalizeErrorRow(row.rawRow));
                }
            }
        }

        /**
         * 规范化错误行，移除内部键列。
         * @param row 原始行
         * * @return 规范化后的行
         */
        private List<String> normalizeErrorRow(List<String> row) {
            if (row == null) {
                return List.of();
            }
            if (internalKeyIndex == null || internalKeyIndex < 0 || internalKeyIndex >= row.size()) {
                return new ArrayList<>(row);
            }
            List<String> normalized = new ArrayList<>(row);
            normalized.remove((int) internalKeyIndex);
            return normalized;
        }

        /**
         * 安静关闭查询结果集。
         * @param dataSet 结果集         */
        private void closeQuietly(QueryDataSet dataSet) {
            if (dataSet == null) {
                return;
            }
            try {
                dataSet.close();
            } catch (Exception ignored) {
            }
        }

        /**
         * 安静读取下一行。
         * @param dataSet 结果集
         * * @return 下一行或 null
         */
        private Object[] nextRowQuietly(QueryDataSet dataSet) {
            if (dataSet == null) {
                return null;
            }
            try {
                return dataSet.nextRow();
            } catch (Exception ex) {
                return null;
            }
        }

        /**
         * 构建结构化导入结果，必要时生成错误文件。
         * @return 导入结果
         */
        private DataImportResultVO buildResult() {
            DataImportResultVO result = new DataImportResultVO();
            result.setTotal(total);
            result.setSuccess(success);
            result.setFailed(failed);
            if (!errorRows.isEmpty()) {
                DataFileStorageService.StoredFile file = fileStorageService.createFile("import_error", ".csv");
                try (BufferedWriter writer = Files.newBufferedWriter(file.path(), StandardCharsets.UTF_8)) {
                    List<String> header = new ArrayList<>();
                    header.add("error_message");
                    header.addAll(columns);
                    writer.write(header.stream().map(CsvUtils::toCsvValue).collect(Collectors.joining(",")));
                    writer.newLine();
                    for (int i = 0; i < errorRows.size(); i++) {
                        List<String> row = new ArrayList<>();
                        row.add(errorMessages.get(i));
                        row.addAll(errorRows.get(i));
                        writer.write(row.stream().map(CsvUtils::toCsvValue).collect(Collectors.joining(",")));
                        writer.newLine();
                    }
                    result.setErrorFile(file.fileName());
                    result.setErrorFileUrl("/api/v1/data/files/" + file.fileName());
                } catch (Exception ex) {
                    throw BizException.internal("结构化导入错误文件生成失败: " + ex.getMessage());
                }
            }
            return result;
        }
    }
}

