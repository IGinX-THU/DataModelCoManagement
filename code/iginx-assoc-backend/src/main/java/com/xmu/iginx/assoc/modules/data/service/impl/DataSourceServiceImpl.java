package com.xmu.iginx.assoc.modules.data.service.impl;

import com.xmu.iginx.assoc.common.PageResult;
import cn.edu.tsinghua.iginx.session.QueryDataSet;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceCreateRequest;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceQueryRequest;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceUpdateRequest;
import com.xmu.iginx.assoc.modules.data.entity.DataResourceEntity;
import com.xmu.iginx.assoc.modules.data.enums.DataSourceType;
import com.xmu.iginx.assoc.modules.data.repository.DataResourceRepository;
import com.xmu.iginx.assoc.modules.data.service.DataSourceConnectionTestService;
import com.xmu.iginx.assoc.modules.data.service.DataSourceService;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.util.ConnectionConfigCipher;
import com.xmu.iginx.assoc.modules.data.util.IginxStorageEngineHelper;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredQueryHelper;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredUtils;
import com.xmu.iginx.assoc.modules.data.util.StorageEngineFlagsValidator;
import com.xmu.iginx.assoc.modules.data.util.TimeSeriesPathUtils;
import com.xmu.iginx.assoc.modules.data.vo.DataSourceConnectionConfigVO;
import com.xmu.iginx.assoc.modules.data.vo.DataSourceStructureNodeVO;
import com.xmu.iginx.assoc.modules.data.vo.DataSourceVO;
import com.xmu.iginx.assoc.modules.relation.repository.AssociationRuleRepository;
import cn.edu.tsinghua.iginx.session.Column;
import cn.edu.tsinghua.iginx.session.ClusterInfo;
import cn.edu.tsinghua.iginx.thrift.StorageEngineInfo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 数据源服务实现，负责数据源的增删改查与结构信息获取。
 */
@Service
@RequiredArgsConstructor
public class DataSourceServiceImpl implements DataSourceService {

    private final DataResourceRepository dataResourceRepository;
    private final DataSourceConnectionTestService connectionTestService;
    private final ConnectionConfigCipher connectionConfigCipher;
    private final AssociationRuleRepository associationRuleRepository;
    private final IginxStorageWrapper iginxStorageWrapper;
    private final IginxStorageEngineHelper storageEngineHelper;
    private final IginxStructuredQueryHelper structuredQueryHelper;

    /**
     * 新增数据源并完成必要的挂载与存储引擎注册。
     *
     * @param request 新增请求
     * @return 数据源 ID
     */
    @Override
    @Transactional
    public Long createDataSource(DataSourceCreateRequest request) {
        validateRequest(request.getSourceType(), request.getConnectionConfig());
        validateUniqueName(request.getName(), null);
        // 先进行连接性校验，避免无效配置落库
        connectionTestService.testConnection(request.getSourceType(), request.getConnectionConfig());
        DataSourceType sourceType = resolveSourceType(request.getSourceType());
        // 解析并规范化挂载路径
        String mountPath = resolveMountPathForCreate(request.getMountPath(), sourceType, request.getConnectionConfig());
        validateMountPath(mountPath, null);
        // 检查是否存在残留或冲突的存储引擎，避免挂载冲突
        ensureNoConflictingResidualEngines(sourceType, mountPath);

        if (isTimeSeriesSource(sourceType)) {
            if (!storageEngineExists(sourceType, request.getConnectionConfig(), mountPath)) {
                // 仅当 IGinX 未注册该引擎时才执行注册
                String addSql = storageEngineHelper.buildAddStorageEngineSql(
                    sourceType,
                    request.getConnectionConfig(),
                    mountPath);
                iginxStorageWrapper.executeSql(addSql);
            }
        }

        DataResourceEntity entity = new DataResourceEntity();
        entity.setName(request.getName());
        entity.setSourceType(request.getSourceType().toUpperCase());
        entity.setMountPath(mountPath);
        entity.setDescription(request.getDescription());
        entity.setConnConfig(connectionConfigCipher.encrypt(request.getConnectionConfig()));
        entity.setCreateTime(LocalDateTime.now());

        DataResourceEntity saved = dataResourceRepository.save(entity);
        return saved.getId();
    }

    /**
     * 分页查询数据源列表。
     *
     * @param request 查询请求
     * @return 分页结果
     */
    @Override
    public PageResult<DataSourceVO> pageDataSources(DataSourceQueryRequest request) {
        Pageable pageable = PageRequest.of(request.getPageNum() - 1, request.getPageSize());
        Specification<DataResourceEntity> specification = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (request.getName() != null && !request.getName().isBlank()) {
                predicates = cb.and(predicates, cb.like(root.get("name"), "%" + request.getName().trim() + "%"));
            }
            if (request.getSourceType() != null && !request.getSourceType().isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("sourceType"), request.getSourceType().trim().toUpperCase()));
            }
            return predicates;
        };

        Page<DataResourceEntity> page = dataResourceRepository.findAll(specification, pageable);
        return PageResult.of(
            page.getContent().stream().map(this::toVO).toList(),
            page.getTotalElements(),
            request.getPageNum(),
            request.getPageSize()
        );
    }

    /**
     * 获取单个数据源详情。
     *
     * @param id 数据源 ID
     * @return 数据源信息
     */
    @Override
    public DataSourceVO getDataSource(Long id) {
        DataResourceEntity entity = findById(id);
        return toVO(entity);
    }

    /**
     * 更新数据源信息。
     *
     * @param id 数据源 ID
     * @param request 更新请求
     */
    @Override
    @Transactional
    public void updateDataSource(Long id, DataSourceUpdateRequest request) {
        DataResourceEntity entity = findById(id);
        validateUniqueName(request.getName(), id);

        entity.setName(request.getName());
        entity.setDescription(request.getDescription());

        DataSourceConnectionConfig connectionConfig = request.getConnectionConfig();
        if (connectionConfig != null) {
            StorageEngineFlagsValidator.validate(connectionConfig);
            // 连接配置更新前先做连通性校验
            connectionTestService.testConnection(entity.getSourceType(), connectionConfig);
            entity.setConnConfig(connectionConfigCipher.encrypt(connectionConfig));
        }
        dataResourceRepository.save(entity);
    }

    /**
     * 删除数据源，必要时卸载 IGinX 存储引擎。
     *
     * @param id 数据源 ID
     * @param force 是否强制删除
     */
    @Override
    @Transactional
    public void removeDataSource(Long id, boolean force) {
        DataResourceEntity entity = findById(id);
        boolean inUse = associationRuleRepository.existsByDataId(id);
        if (inUse && !force) {
            throw BizException.badRequest("该数据源正被关联规则占用，无法删除");
        }
        DataSourceType sourceType = resolveSourceType(entity.getSourceType());
        if (needsStorageEngineUnregister(sourceType)) {
            if (!force) {
                // 非强制删除时，要求卸载成功
                removeStorageEngine(entity);
            } else {
                try {
                    // 强制删除时，卸载失败仅记录，不阻断删除
                    removeStorageEngine(entity);
                } catch (BizException ex) {
                    // 强制删除时忽略 IGinX 卸载失败
                }
            }
        }
        dataResourceRepository.delete(entity);
    }

    /**
     * 测试数据源连接是否可用。
     *
     * @param sourceType 数据源类型
     * @param config 连接配置
     */
    @Override
    public void testConnection(String sourceType, DataSourceConnectionConfig config) {
        validateRequest(sourceType, config);
        connectionTestService.testConnection(sourceType, config);
    }

    /**
     * 获取数据源结构树。
     *
     * @param id 数据源 ID
     * @return 结构节点列表
     */
    @Override
    public List<DataSourceStructureNodeVO> listStructure(Long id) {
        DataResourceEntity entity = findById(id);
        DataSourceType sourceType;
        try {
            sourceType = DataSourceType.valueOf(entity.getSourceType().toUpperCase());
        } catch (Exception ex) {
            throw BizException.badRequest("不支持的数据源类型: " + entity.getSourceType());
        }

        if (sourceType == DataSourceType.POSTGRESQL) {
            // 结构化数据源使用 SHOW COLUMNS 构建树
            return listPostgresStructure(entity.getId(), entity.getMountPath());
        }
        // 时序数据源以测点路径构建树
        String normalizedMount = normalizeTimeSeriesMountPath(entity.getMountPath(), sourceType);
        return listTimeSeriesStructure(normalizedMount);
    }

    /**
     * 校验数据源类型与连接配置的基本合法性。
     *
     * @param sourceType 数据源类型
     * @param connectionConfig 连接配置
     */
    private void validateRequest(String sourceType, DataSourceConnectionConfig connectionConfig) {
        if (!DataSourceType.isSupported(sourceType)) {
            throw BizException.badRequest("不支持的数据源类型: " + sourceType);
        }
        if (connectionConfig == null) {
            throw BizException.badRequest("连接配置不能为空");
        }
        StorageEngineFlagsValidator.validate(connectionConfig);
    }

    /**
     * 校验数据源名称在库中唯一。
     *
     * @param name 数据源名称
     * @param id 当前数据源 ID（更新时用于排除自己）
     */
    private void validateUniqueName(String name, Long id) {
        boolean exists = id == null
            ? dataResourceRepository.existsByName(name)
            : dataResourceRepository.existsByNameAndIdNot(name, id);
        if (exists) {
            throw BizException.badRequest("数据源名称已存在");
        }
    }

    /**
     * 校验挂载路径合法且唯一。
     *
     * @param mountPath 挂载路径
     * @param id 当前数据源 ID（更新时用于排除自己）
     */
    private void validateMountPath(String mountPath, Long id) {
        if (mountPath == null || mountPath.isBlank()) {
            throw BizException.badRequest("挂载路径不能为空");
        }
        dataResourceRepository.findByMountPath(mountPath)
            .filter(entity -> !Objects.equals(entity.getId(), id))
            .ifPresent(entity -> {
                throw BizException.badRequest("挂载别名已存在，请更换");
            });
    }

    /**
     * 解析创建时的挂载路径，按数据源类型进行规范化处理。
     *
     * @param mountPath 原始挂载路径
     * @param sourceType 数据源类型
     * @param config 连接配置
     * @return 规范化后的挂载路径
     */
    private String resolveMountPathForCreate(String mountPath,
                                             DataSourceType sourceType,
                                             DataSourceConnectionConfig config) {
        // 先统一路径格式，避免空格或多余分隔符影响判断
        String normalized = TimeSeriesPathUtils.normalizePath(mountPath);
        if (normalized.isBlank()) {
            throw BizException.badRequest("挂载路径不能为空");
        }
        if (sourceType == DataSourceType.IOTDB) {
            String lower = normalized.trim().toLowerCase(Locale.ROOT);
            if ("root".equals(lower)) {
                throw BizException.badRequest("挂载路径不能以 root 结尾，请填写 root.xxx 或 xxx");
            }
            // IoTDB 挂载路径要求以 root.xxx 形式存在
            String stripped = TimeSeriesPathUtils.normalizeIotdbMountPath(normalized);
            if (stripped.isBlank()) {
                throw BizException.badRequest("挂载路径不能为空");
            }
            return stripped;
        }
        if (sourceType == DataSourceType.INFLUXDB) {
            String lower = normalized.trim().toLowerCase(Locale.ROOT);
            if ("root".equals(lower)) {
                throw BizException.badRequest("挂载路径必须为 root.xxx，不能仅 root");
            }
            if (!TimeSeriesPathUtils.hasRootPrefix(normalized)) {
                // 未带 root 前缀时自动补齐
                return "root." + normalized;
            }
            if (!normalized.startsWith("root.")) {
                // 统一大小写形式为 root.xxx
                String suffix = normalized.substring(normalized.indexOf('.') + 1);
                return "root." + suffix;
            }
        }
        return normalized;
    }

    /**
     * 将字符串解析为数据源类型枚举。
     *
     * @param sourceType 数据源类型字符串
     * @return 枚举类型，无法解析时返回 null
     */
    private DataSourceType resolveSourceType(String sourceType) {
        if (sourceType == null) {
            return null;
        }
        try {
            return DataSourceType.valueOf(sourceType.trim().toUpperCase());
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 判断是否为时序数据源。
     *
     * @param sourceType 数据源类型
     * @return 是否时序数据源
     */
    private boolean isTimeSeriesSource(DataSourceType sourceType) {
        return sourceType == DataSourceType.IOTDB || sourceType == DataSourceType.INFLUXDB;
    }

    /**
     * 判断删除数据源时是否需要卸载存储引擎。
     *
     * @param sourceType 数据源类型
     * @return 是否需要卸载
     */
    private boolean needsStorageEngineUnregister(DataSourceType sourceType) {
        return sourceType == DataSourceType.IOTDB
            || sourceType == DataSourceType.INFLUXDB
            || sourceType == DataSourceType.POSTGRESQL;
    }

    /**
     * 规范化时序数据源的挂载路径。
     *
     * @param mountPath 挂载路径
     * @param sourceType 数据源类型
     * @return 规范化后的挂载路径
     */
    private String normalizeTimeSeriesMountPath(String mountPath, DataSourceType sourceType) {
        if (sourceType == DataSourceType.IOTDB) {
            return TimeSeriesPathUtils.normalizeIotdbMountPath(mountPath);
        }
        return mountPath;
    }

    /**
     * 按 ID 获取数据源实体，不存在则抛异常。
     *
     * @param id 数据源 ID
     * @return 数据源实体
     */
    private DataResourceEntity findById(Long id) {
        return dataResourceRepository.findById(id)
            .orElseThrow(() -> BizException.badRequest("数据源不存在，id=" + id));
    }

    /**
     * 读取 PostgreSQL 数据源的结构信息，并构建 schema/table 树。
     *
     * @param sourceId 数据源 ID
     * @param mountPath 挂载路径
     * @return 结构树
     */
    private List<DataSourceStructureNodeVO> listPostgresStructure(Long sourceId, String mountPath) {
        QueryDataSet dataSet = structuredQueryHelper.executeQuery("SHOW COLUMNS;", 1000);
        try {
            List<String> headers = dataSet.getColumnList();
            if (headers == null || headers.isEmpty()) {
                return List.of();
            }
            int pathIndex = indexOfIgnoreCase(headers, "Path");
            if (pathIndex < 0) {
                return List.of();
            }
            List<String> mountSegments = IginxStructuredUtils.splitPathSegments(mountPath);
            Map<String, Set<String>> allTables = new LinkedHashMap<>();
            Map<String, Set<String>> matchedTables = new LinkedHashMap<>();
            while (dataSet.hasMore()) {
                Object[] row = dataSet.nextRow();
                if (row == null) {
                    continue;
                }
                if (row.length <= pathIndex) {
                    continue;
                }
                String rawPath = toStringValue(row[pathIndex]);
                if (rawPath == null || rawPath.isBlank()) {
                    continue;
                }
                List<String> segments = IginxStructuredUtils.splitPathSegments(rawPath);
                if (segments.size() < 2) {
                    continue;
                }
                if ("root".equalsIgnoreCase(segments.get(0))) {
                    continue;
                }
                String columnName = segments.get(segments.size() - 1);
                if (IginxStructuredUtils.isInternalKey(columnName)) {
                    continue;
                }
                List<String> tableSegments = segments.subList(0, segments.size() - 1);
                boolean matchMount = !mountSegments.isEmpty()
                    && IginxStructuredUtils.startsWithSegments(tableSegments, mountSegments);
                if (matchMount) {
                    // 去除挂载路径前缀，避免重复
                    tableSegments = tableSegments.subList(mountSegments.size(), tableSegments.size());
                }
                if (tableSegments.isEmpty()) {
                    continue;
                }
                String schema;
                String table;
                if (tableSegments.size() >= 2) {
                    schema = tableSegments.get(0);
                    table = String.join(".", tableSegments.subList(1, tableSegments.size()));
                } else {
                    // 没有明确 schema 时默认 public
                    schema = "public";
                    table = tableSegments.get(0);
                }
                addTable(allTables, schema, table);
                if (matchMount) {
                    addTable(matchedTables, schema, table);
                }
            }
            Map<String, Set<String>> target = (!mountSegments.isEmpty() && !matchedTables.isEmpty())
                ? matchedTables
                : allTables;
            List<DataSourceStructureNodeVO> schemaNodes = new ArrayList<>();
            List<String> schemas = new ArrayList<>(target.keySet());
            schemas.sort(String::compareToIgnoreCase);
            for (String schemaName : schemas) {
                Set<String> tableSet = target.get(schemaName);
                List<DataSourceStructureNodeVO> tableNodes = new ArrayList<>();
                List<String> tables = new ArrayList<>(tableSet);
                tables.sort(String::compareToIgnoreCase);
                for (String tableName : tables) {
                    DataSourceStructureNodeVO tableNode = new DataSourceStructureNodeVO();
                    tableNode.setId(sourceId + "." + schemaName + "." + tableName);
                    tableNode.setName(tableName);
                    tableNode.setType("table");
                    tableNodes.add(tableNode);
                }
                DataSourceStructureNodeVO schemaNode = new DataSourceStructureNodeVO();
                schemaNode.setId(sourceId + "." + schemaName);
                schemaNode.setName(schemaName);
                schemaNode.setType("schema");
                schemaNode.setChildren(tableNodes);
                schemaNodes.add(schemaNode);
            }
            return schemaNodes;
        } catch (Exception ex) {
            String message = extractMessage(ex);
            if (message != null && message.contains("Index 0 out of bounds")) {
                return List.of();
            }
            throw BizException.internal("获取数据源表结构失败: " + ex.getMessage());
        } finally {
            closeQuietly(dataSet);
        }
    }

    /**
     * 向结构映射中添加表名。
     *
     * @param target 结构映射
     * @param schema schema 名称
     * @param table 表名
     */
    private void addTable(Map<String, Set<String>> target, String schema, String table) {
        if (schema == null || schema.isBlank() || table == null || table.isBlank()) {
            return;
        }
        target.computeIfAbsent(schema, key -> new java.util.LinkedHashSet<>()).add(table);
    }

    /**
     * 忽略大小写查找列名索引。
     *
     * @param headers 表头
     * @param target 目标列名
     * @return 索引，找不到返回 -1
     */
    private int indexOfIgnoreCase(List<String> headers, String target) {
        if (headers == null || target == null) {
            return -1;
        }
        for (int i = 0; i < headers.size(); i++) {
            if (target.equalsIgnoreCase(headers.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 将字段值转换为字符串。
     *
     * @param value 原始值
     * @return 字符串值
     */
    private String toStringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    /**
     * 提取异常链中最有意义的消息。
     *
     * @param ex 异常
     * @return 异常消息
     */
    private String extractMessage(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * 安静关闭查询结果集。
     *
     * @param dataSet 查询结果集
     */
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
     * 读取时序数据源结构并构建层级树。
     *
     * @param mountPath 挂载路径
     * @return 结构树
     */
    private List<DataSourceStructureNodeVO> listTimeSeriesStructure(String mountPath) {
        List<Column> columns = iginxStorageWrapper.executeWithSession(session -> session.showColumns());
        List<DataSourceStructureNodeVO> roots = new ArrayList<>();
        var nodeMap = new java.util.LinkedHashMap<String, DataSourceStructureNodeVO>();
        List<String> prefixSegments = splitSegments(normalizeTimeSeriesPath(mountPath));
        for (Column column : columns) {
            String fullPath = column.getPath();
            if (fullPath == null || fullPath.isBlank()) {
                continue;
            }
            String normalizedPath = normalizeTimeSeriesPath(fullPath);
            boolean isInit = normalizedPath.endsWith(".__init__");
            if (isInit) {
                normalizedPath = normalizedPath.substring(0, normalizedPath.length() - ".__init__".length());
            }
            if (normalizedPath.isBlank()) {
                continue;
            }
            List<String> normalizedSegments = splitSegments(normalizedPath);
            if (!prefixSegments.isEmpty() && !startsWithSegments(normalizedSegments, prefixSegments)) {
                continue;
            }
            List<String> fullSegments = splitSegments(fullPath);
            if (isInit && !fullSegments.isEmpty()) {
                fullSegments = new ArrayList<>(fullSegments.subList(0, fullSegments.size() - 1));
            }
            if (fullSegments.isEmpty()) {
                continue;
            }
            int fullOffset = 0;
            if ("root".equalsIgnoreCase(fullSegments.get(0))) {
                fullOffset = 1;
            }
            List<String> relativeSegments = normalizedSegments.subList(prefixSegments.size(), normalizedSegments.size());
            if (relativeSegments.isEmpty()) {
                continue;
            }

            DataSourceStructureNodeVO parent = null;
            if (!prefixSegments.isEmpty()) {
                int prefixEnd = Math.min(fullOffset + prefixSegments.size(), fullSegments.size());
                if (prefixEnd > 0) {
                    String prefixPath = joinSegments(fullSegments, prefixEnd);
                    DataSourceStructureNodeVO prefixNode = nodeMap.get(prefixPath);
                    if (prefixNode == null) {
                        prefixNode = new DataSourceStructureNodeVO();
                        prefixNode.setId(prefixPath);
                        prefixNode.setName(fullSegments.get(prefixEnd - 1));
                        prefixNode.setChildren(new ArrayList<>());
                        prefixNode.setType("group");
                        nodeMap.put(prefixPath, prefixNode);
                        roots.add(prefixNode);
                    }
                    parent = prefixNode;
                }
            }

            for (int i = 0; i < relativeSegments.size(); i++) {
                int fullIndex = fullOffset + prefixSegments.size() + i;
                if (fullIndex >= fullSegments.size()) {
                    break;
                }
                String currentPath = joinSegments(fullSegments, fullIndex + 1);
                DataSourceStructureNodeVO node = nodeMap.get(currentPath);
                if (node == null) {
                    node = new DataSourceStructureNodeVO();
                    node.setId(currentPath);
                    node.setName(fullSegments.get(fullIndex));
                    node.setChildren(new ArrayList<>());
                    nodeMap.put(currentPath, node);
                    if (parent == null) {
                        roots.add(node);
                    } else {
                        parent.getChildren().add(node);
                    }
                }
                // 末级节点为测点，其余为分组
                String desiredType = i == relativeSegments.size() - 1
                    ? (isInit ? "group" : "point")
                    : "group";
                if (node.getType() == null || ("group".equals(node.getType()) && "point".equals(desiredType))) {
                    node.setType(desiredType);
                }
                parent = node;
            }
        }
        return roots;
    }

    /**
     * 规范化时序路径，剥离 root 前缀。
     *
     * @param path 原始路径
     * @return 规范化后的路径
     */
    private String normalizeTimeSeriesPath(String path) {
        if (path == null) {
            return "";
        }
        String trimmed = path.trim();
        if (trimmed.startsWith("root.")) {
            return trimmed.substring("root.".length());
        }
        return trimmed;
    }

    /**
     * 将路径按 "." 拆分为段。
     *
     * @param path 路径
     * @return 段列表
     */
    private List<String> splitSegments(String path) {
        if (path == null) {
            return List.of();
        }
        String trimmed = path.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        String[] raw = trimmed.split("\\.");
        List<String> segments = new ArrayList<>();
        for (String segment : raw) {
            if (segment != null && !segment.isBlank()) {
                segments.add(segment.trim());
            }
        }
        return segments;
    }

    /**
     * 判断 segments 是否以 prefix 作为前缀。
     *
     * @param segments 原始段列表
     * @param prefix 前缀段列表
     * @return 是否匹配前缀
     */
    private boolean startsWithSegments(List<String> segments, List<String> prefix) {
        if (prefix.size() > segments.size()) {
            return false;
        }
        for (int i = 0; i < prefix.size(); i++) {
            if (!prefix.get(i).equals(segments.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 将段列表拼接为路径。
     *
     * @param segments 段列表
     * @param endExclusive 结束下标（不包含）
     * @return 拼接后的路径
     */
    private String joinSegments(List<String> segments, int endExclusive) {
        if (segments == null || segments.isEmpty() || endExclusive <= 0) {
            return "";
        }
        int end = Math.min(endExclusive, segments.size());
        return String.join(".", segments.subList(0, end));
    }

    /**
     * 卸载数据源对应的 IGinX 存储引擎。
     *
     * @param entity 数据源实体
     */
    private void removeStorageEngine(DataResourceEntity entity) {
        DataSourceConnectionConfig config = connectionConfigCipher.decrypt(entity.getConnConfig());
        DataSourceType sourceType = resolveSourceType(entity.getSourceType());
        if (sourceType == null) {
            return;
        }
        boolean existsBefore = storageEngineExistsByEndpoint(sourceType, config);
        if (!existsBefore) {
            return;
        }
        String mountPath = entity.getMountPath();
        String normalizedMount = normalizeTimeSeriesMountPath(mountPath, sourceType);
        LinkedHashSet<String> prefixes = buildRemovePrefixCandidates(mountPath, normalizedMount, sourceType);
        if (prefixes.isEmpty()) {
            prefixes.add("");
        }
        LinkedHashSet<String> sqlSet = new LinkedHashSet<>();
        for (String prefix : prefixes) {
            // 依据 IGinX 手册 3.3.5：host/port/schemaPrefix/dataPrefix 四元组唯一确定待移除分片
            sqlSet.add(storageEngineHelper.buildRemoveStorageEngineSql(config, prefix, prefix, false));
            if (!prefix.isBlank()) {
                sqlSet.add(storageEngineHelper.buildRemoveStorageEngineSql(config, "", prefix, false));
                sqlSet.add(storageEngineHelper.buildRemoveStorageEngineSql(config, prefix, "", false));
            }
        }
        // 手册要求当 schemaPrefix/dataPrefix 为空时使用空字符串
        sqlSet.add(storageEngineHelper.buildRemoveStorageEngineSql(config, "", "", false));
        List<String> sqlList = new ArrayList<>(sqlSet);
        BizException lastException = null;
        boolean removed = false;
        for (String sql : sqlList) {
            try {
                iginxStorageWrapper.executeSql(sql);
                removed = true;
                break;
            } catch (BizException ex) {
                if (shouldIgnoreRemoveError(ex)) {
                    lastException = ex;
                    continue;
                }
                throw ex;
            }
        }
        if (removed) {
            return;
        }
        if (!storageEngineExistsByEndpoint(sourceType, config)) {
            return;
        }
        if (lastException != null && lastException.getMessage() != null && !lastException.getMessage().isBlank()) {
            throw BizException.badRequest("IGinX 存储引擎卸载失败，目标引擎仍存在: " + lastException.getMessage());
        }
        throw BizException.badRequest("IGinX 存储引擎卸载失败，目标引擎仍存在");
    }

    /**
     * 构建卸载存储引擎时的前缀候选集合。
     *
     * @param mountPath 原始挂载路径
     * @param normalizedMount 规范化挂载路径
     * @param sourceType 数据源类型
     * @return 前缀候选集合
     */
    private LinkedHashSet<String> buildRemovePrefixCandidates(String mountPath,
                                                              String normalizedMount,
                                                              DataSourceType sourceType) {
        LinkedHashSet<String> prefixes = new LinkedHashSet<>();
        addNormalizedPrefix(prefixes, mountPath);
        addNormalizedPrefix(prefixes, normalizedMount);
        if (sourceType == DataSourceType.IOTDB) {
            List<String> snapshot = new ArrayList<>(prefixes);
            for (String prefix : snapshot) {
                addIotdbRootVariants(prefixes, prefix);
            }
        }
        return prefixes;
    }

    /**
     * 规范化并追加前缀。
     *
     * @param prefixes 前缀集合
     * @param rawPrefix 原始前缀
     */
    private void addNormalizedPrefix(LinkedHashSet<String> prefixes, String rawPrefix) {
        if (rawPrefix == null) {
            return;
        }
        String normalized = TimeSeriesPathUtils.normalizePath(rawPrefix);
        if (!normalized.isBlank()) {
            prefixes.add(normalized);
        }
    }

    /**
     * 为 IoTDB 挂载路径补充 root 前缀变体。
     *
     * @param prefixes 前缀集合
     * @param prefix 原始前缀
     */
    private void addIotdbRootVariants(LinkedHashSet<String> prefixes, String prefix) {
        String normalized = TimeSeriesPathUtils.normalizePath(prefix);
        if (normalized.isBlank()) {
            return;
        }
        if (TimeSeriesPathUtils.hasRootPrefix(normalized)) {
            String stripped = TimeSeriesPathUtils.stripRootPrefix(normalized);
            if (!stripped.isBlank()) {
                prefixes.add(stripped);
            }
            return;
        }
        prefixes.add("root." + normalized);
    }

    /**
     * 判断指定挂载路径的存储引擎是否已注册。
     *
     * @param sourceType 数据源类型
     * @param config 连接配置
     * @param mountPath 挂载路径
     * @return 是否已注册
     */
    private boolean storageEngineExists(DataSourceType sourceType,
                                        DataSourceConnectionConfig config,
                                        String mountPath) {
        String resolvedHost = storageEngineHelper.resolveStorageHost(config.getHost());
        String engineType = storageEngineHelper.resolveEngineType(sourceType);
        int port = config.getPort() == null ? -1 : config.getPort();
        String normalizedMount = normalizeTimeSeriesMountPath(mountPath, sourceType);
        String expectedPrefix = normalizePrefix(normalizedMount);
        try {
            return iginxStorageWrapper.executeWithSession(session -> {
                ClusterInfo clusterInfo = session.getClusterInfo();
                List<StorageEngineInfo> infos = clusterInfo == null ? null : clusterInfo.getStorageEngineInfos();
                if (infos == null || infos.isEmpty()) {
                    return false;
                }
                for (StorageEngineInfo info : infos) {
                    String infoType = info.getType() == null ? "" : info.getType().toString();
                    if (!engineType.equalsIgnoreCase(infoType)) {
                        continue;
                    }
                    if (resolvedHost == null || info.getIp() == null) {
                        continue;
                    }
                    if (!resolvedHost.equalsIgnoreCase(info.getIp())
                        && !isHostAliasMatch(config.getHost(), info.getIp())) {
                        continue;
                    }
                    if (info.getPort() != port) {
                        continue;
                    }
                    String schemaPrefix = normalizePrefix(info.getSchemaPrefix());
                    String dataPrefix = normalizePrefix(info.getDataPrefix());
                    // schemaPrefix 允许为空时视为通配，dataPrefix 必须匹配挂载前缀
                    boolean schemaMatch = expectedPrefix.equals(schemaPrefix) || schemaPrefix.isEmpty();
                    if (expectedPrefix.equals(dataPrefix) && schemaMatch) {
                        return true;
                    }
                }
                return false;
            });
        } catch (BizException ex) {
            if (isClusterInfoIncompatible(ex)) {
                // 兼容旧版 IGinX：无法读取集群信息时默认按未注册处理
                return false;
            }
            throw ex;
        }
    }

    /**
     * 判断指定主机端口的存储引擎是否存在。
     *
     * @param sourceType 数据源类型
     * @param config 连接配置
     * @return 是否存在
     */
    private boolean storageEngineExistsByEndpoint(DataSourceType sourceType,
                                                  DataSourceConnectionConfig config) {
        String resolvedHost = storageEngineHelper.resolveStorageHost(config.getHost());
        String engineType = storageEngineHelper.resolveEngineType(sourceType);
        int port = config.getPort() == null ? -1 : config.getPort();
        try {
            return iginxStorageWrapper.executeWithSession(session -> {
                ClusterInfo clusterInfo = session.getClusterInfo();
                List<StorageEngineInfo> infos = clusterInfo == null ? null : clusterInfo.getStorageEngineInfos();
                if (infos == null || infos.isEmpty()) {
                    return false;
                }
                for (StorageEngineInfo info : infos) {
                    String infoType = info.getType() == null ? "" : info.getType().toString();
                    if (!engineType.equalsIgnoreCase(infoType)) {
                        continue;
                    }
                    if (resolvedHost == null || info.getIp() == null) {
                        continue;
                    }
                    if (!resolvedHost.equalsIgnoreCase(info.getIp())
                        && !isHostAliasMatch(config.getHost(), info.getIp())) {
                        continue;
                    }
                    if (info.getPort() != port) {
                        continue;
                    }
                    return true;
                }
                return false;
            });
        } catch (BizException ex) {
            if (isClusterInfoIncompatible(ex)) {
                return false;
            }
            throw ex;
        }
    }

    /**
     * 检查是否存在冲突或残留的存储引擎。
     *
     * @param sourceType 数据源类型
     * @param mountPath 挂载路径
     */
    private void ensureNoConflictingResidualEngines(DataSourceType sourceType, String mountPath) {
        if (sourceType == null || mountPath == null || mountPath.isBlank()) {
            return;
        }
        String normalizedMount = normalizeTimeSeriesMountPath(mountPath, sourceType);
        LinkedHashSet<String> targetPrefixes = buildRemovePrefixCandidates(mountPath, normalizedMount, sourceType);
        if (targetPrefixes.isEmpty()) {
            return;
        }
        String expectedEngineType = storageEngineHelper.resolveEngineType(sourceType);
        try {
            boolean hasConflict = iginxStorageWrapper.executeWithSession(session -> {
                ClusterInfo clusterInfo = session.getClusterInfo();
                List<StorageEngineInfo> infos = clusterInfo == null ? null : clusterInfo.getStorageEngineInfos();
                if (infos == null || infos.isEmpty()) {
                    return false;
                }
                for (StorageEngineInfo info : infos) {
                    String infoType = info.getType() == null ? "" : info.getType().toString();
                    String dataPrefix = normalizePrefix(info.getDataPrefix());
                    if (isTimeSeriesSource(sourceType)
                        && "relational".equalsIgnoreCase(infoType)
                        && dataPrefix.isEmpty()) {
                        // 时序数据源挂载到 relational 空前缀会与结构化引擎冲突
                        return true;
                    }
                    if (!targetPrefixes.contains(dataPrefix)) {
                        continue;
                    }
                    if (!expectedEngineType.equalsIgnoreCase(infoType)) {
                        // 同一前缀下存在不同类型引擎，视为冲突
                        return true;
                    }
                }
                return false;
            });
            if (hasConflict) {
                throw BizException.badRequest("IGinX 中存在冲突或残留存储引擎，请先清理后再创建该挂载路径的数据源");
            }
        } catch (BizException ex) {
            if (isClusterInfoIncompatible(ex)) {
                return;
            }
            throw ex;
        }
    }

    /**
     * 判断是否为集群信息不兼容异常。
     *
     * @param ex 业务异常
     * @return 是否不兼容
     */
    private boolean isClusterInfoIncompatible(BizException ex) {
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("connectable") && lower.contains("iginxinfo");
    }

    /**
     * 判断卸载存储引擎异常是否可忽略。
     *
     * @param ex 业务异常
     * @return 是否可忽略
     */
    private boolean shouldIgnoreRemoveError(BizException ex) {
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("dummy storage engine")
            || lower.contains("not read-only")
            || lower.contains("does not exist")
            || lower.contains("has no data")
            || lower.contains("remove history data source failed");
    }

    /**
     * 判断主机别名是否匹配（处理本地与 Docker 场景）。
     *
     * @param rawHost 配置主机
     * @param actualHost 实际主机
     * @return 是否匹配
     */
    private boolean isHostAliasMatch(String rawHost, String actualHost) {
        if (rawHost == null || actualHost == null) {
            return false;
        }
        String raw = rawHost.trim().toLowerCase(Locale.ROOT);
        String actual = actualHost.trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty() || actual.isEmpty()) {
            return false;
        }
        if (("127.0.0.1".equals(raw) || "localhost".equals(raw))
            && ("host.docker.internal".equals(actual) || "192.168.65.254".equals(actual))) {
            return true;
        }
        if ("host.docker.internal".equals(raw) && "host.docker.internal".equals(actual)) {
            return true;
        }
        if ("host.docker.internal".equals(raw) && actual.equals("192.168.65.254")) {
            return true;
        }
        return false;
    }

    /**
     * 规范化前缀字符串。
     *
     * @param prefix 原始前缀
     * @return 规范化后的前缀
     */
    private String normalizePrefix(String prefix) {
        if (prefix == null) {
            return "";
        }
        String normalized = prefix.trim();
        return normalized.isEmpty() ? "" : normalized;
    }

    /**
     * 将数据源实体转换为视图对象。
     *
     * @param entity 实体
     * @return 视图对象
     */
    private DataSourceVO toVO(DataResourceEntity entity) {
        DataSourceConnectionConfig config = connectionConfigCipher.decrypt(entity.getConnConfig());

        DataSourceConnectionConfigVO configVO = new DataSourceConnectionConfigVO();
        configVO.setHost(config.getHost());
        configVO.setPort(config.getPort());
        configVO.setDatabase(config.getDatabase());
        configVO.setUsername(config.getUsername());
        configVO.setPasswordMasked(connectionConfigCipher.maskPassword(entity.getConnConfig()));
        configVO.setHasData(config.getHasData());
        configVO.setReadOnly(config.getReadOnly());
        configVO.setExtra(config.getExtra());

        DataSourceVO vo = new DataSourceVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setSourceType(entity.getSourceType());
        vo.setMountPath(entity.getMountPath());
        vo.setDescription(entity.getDescription());
        vo.setCreateTime(entity.getCreateTime());
        vo.setConnectionConfig(configVO);
        return vo;
    }
}
