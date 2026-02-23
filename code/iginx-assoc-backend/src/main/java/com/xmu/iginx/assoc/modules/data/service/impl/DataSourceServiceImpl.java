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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Set;

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

    @Override
    @Transactional
    public Long createDataSource(DataSourceCreateRequest request) {
        validateRequest(request.getSourceType(), request.getConnectionConfig());
        validateUniqueName(request.getName(), null);
        connectionTestService.testConnection(request.getSourceType(), request.getConnectionConfig());
        DataSourceType sourceType = resolveSourceType(request.getSourceType());
        String mountPath = resolveMountPathForCreate(request.getMountPath(), sourceType, request.getConnectionConfig());
        validateMountPath(mountPath, null);

        if (isTimeSeriesSource(sourceType)) {
            if (!storageEngineExists(sourceType, request.getConnectionConfig(), mountPath)) {
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

    @Override
    public DataSourceVO getDataSource(Long id) {
        DataResourceEntity entity = findById(id);
        return toVO(entity);
    }

    @Override
    @Transactional
    public void updateDataSource(Long id, DataSourceUpdateRequest request) {
        DataResourceEntity entity = findById(id);
        validateUniqueName(request.getName(), id);

        entity.setName(request.getName());
        entity.setDescription(request.getDescription());

        DataSourceConnectionConfig connectionConfig = request.getConnectionConfig();
        if (connectionConfig != null) {
            connectionTestService.testConnection(entity.getSourceType(), connectionConfig);
            entity.setConnConfig(connectionConfigCipher.encrypt(connectionConfig));
        }
        dataResourceRepository.save(entity);
    }

    @Override
    @Transactional
    public void removeDataSource(Long id, boolean force) {
        DataResourceEntity entity = findById(id);
        boolean inUse = associationRuleRepository.existsByDataId(id);
        if (inUse && !force) {
            throw BizException.badRequest("该数据源正被关联规则占用，无法删除");
        }
        DataSourceType sourceType = resolveSourceType(entity.getSourceType());
        if (isTimeSeriesSource(sourceType)) {
            if (!force) {
                removeStorageEngine(entity);
            } else {
                try {
                    removeStorageEngine(entity);
                } catch (BizException ex) {
                    // 强制删除时忽略 IGinX 卸载失败
                }
            }
        }
        dataResourceRepository.delete(entity);
    }

    @Override
    public void testConnection(String sourceType, DataSourceConnectionConfig config) {
        validateRequest(sourceType, config);
        connectionTestService.testConnection(sourceType, config);
    }

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
            return listPostgresStructure(entity.getId(), entity.getMountPath());
        }
        return listTimeSeriesStructure(entity.getMountPath());
    }

    private void validateRequest(String sourceType, DataSourceConnectionConfig connectionConfig) {
        if (!DataSourceType.isSupported(sourceType)) {
            throw BizException.badRequest("不支持的数据源类型: " + sourceType);
        }
        if (connectionConfig == null) {
            throw BizException.badRequest("连接配置不能为空");
        }
    }

    private void validateUniqueName(String name, Long id) {
        boolean exists = id == null
            ? dataResourceRepository.existsByName(name)
            : dataResourceRepository.existsByNameAndIdNot(name, id);
        if (exists) {
            throw BizException.badRequest("数据源名称已存在");
        }
    }

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

    private String resolveMountPathForCreate(String mountPath,
                                             DataSourceType sourceType,
                                             DataSourceConnectionConfig config) {
        String normalized = TimeSeriesPathUtils.normalizePath(mountPath);
        if (normalized.isBlank()) {
            throw BizException.badRequest("挂载路径不能为空");
        }
        if (sourceType == DataSourceType.INFLUXDB || sourceType == DataSourceType.IOTDB) {
            String lower = normalized.trim().toLowerCase(Locale.ROOT);
            if ("root".equals(lower)) {
                throw BizException.badRequest("挂载路径必须为 root.xxx，不能仅 root");
            }
            if (!TimeSeriesPathUtils.hasRootPrefix(normalized)) {
                return "root." + normalized;
            }
            if (!normalized.startsWith("root.")) {
                String suffix = normalized.substring(normalized.indexOf('.') + 1);
                return "root." + suffix;
            }
        }
        return normalized;
    }

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

    private boolean isTimeSeriesSource(DataSourceType sourceType) {
        return sourceType == DataSourceType.IOTDB || sourceType == DataSourceType.INFLUXDB;
    }

    private DataResourceEntity findById(Long id) {
        return dataResourceRepository.findById(id)
            .orElseThrow(() -> BizException.badRequest("数据源不存在，id=" + id));
    }

    private List<DataSourceStructureNodeVO> listPostgresStructure(Long sourceId, String mountPath) {
        QueryDataSet dataSet = structuredQueryHelper.executeQuery("SHOW COLUMNS;", 1000);
        try {
            List<String> headers = dataSet.getColumnList();
            int pathIndex = indexOfIgnoreCase(headers, "Path");
            if (pathIndex < 0) {
                return List.of();
            }
            List<String> mountSegments = IginxStructuredUtils.splitPathSegments(mountPath);
            Map<String, Set<String>> allTables = new LinkedHashMap<>();
            Map<String, Set<String>> matchedTables = new LinkedHashMap<>();
            Object[] row;
            while ((row = dataSet.nextRow()) != null) {
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
            throw BizException.internal("鑾峰彇鏁版嵁婧愯〃缁撴瀯澶辫触: " + ex.getMessage());
        } finally {
            closeQuietly(dataSet);
        }
    }

    private void addTable(Map<String, Set<String>> target, String schema, String table) {
        if (schema == null || schema.isBlank() || table == null || table.isBlank()) {
            return;
        }
        target.computeIfAbsent(schema, key -> new java.util.LinkedHashSet<>()).add(table);
    }

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

    private String toStringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
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

    private String joinSegments(List<String> segments, int endExclusive) {
        if (segments == null || segments.isEmpty() || endExclusive <= 0) {
            return "";
        }
        int end = Math.min(endExclusive, segments.size());
        return String.join(".", segments.subList(0, end));
    }

    private void removeStorageEngine(DataResourceEntity entity) {
        DataSourceConnectionConfig config = connectionConfigCipher.decrypt(entity.getConnConfig());
        String mountPath = entity.getMountPath();
        List<String> sqlList = new ArrayList<>();
        sqlList.add(storageEngineHelper.buildRemoveStorageEngineSql(config, mountPath, mountPath, true));
        if (mountPath != null && !mountPath.isBlank()) {
            sqlList.add(storageEngineHelper.buildRemoveStorageEngineSql(config, "", mountPath, true));
        }
        BizException lastException = null;
        for (String sql : sqlList) {
            try {
                iginxStorageWrapper.executeSql(sql);
                lastException = null;
            } catch (BizException ex) {
                if (shouldIgnoreRemoveError(ex)) {
                    continue;
                }
                lastException = ex;
            }
        }
        if (lastException != null) {
            throw lastException;
        }
    }

    private boolean storageEngineExists(DataSourceType sourceType,
                                        DataSourceConnectionConfig config,
                                        String mountPath) {
        String resolvedHost = storageEngineHelper.resolveStorageHost(config.getHost());
        String engineType = storageEngineHelper.resolveEngineType(sourceType);
        int port = config.getPort() == null ? -1 : config.getPort();
        String expectedPrefix = normalizePrefix(mountPath);
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
                boolean schemaMatch = expectedPrefix.equals(schemaPrefix) || schemaPrefix.isEmpty();
                if (expectedPrefix.equals(dataPrefix) && schemaMatch) {
                    return true;
                }
            }
            return false;
        });
    }

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

    private String normalizePrefix(String prefix) {
        if (prefix == null) {
            return "";
        }
        String normalized = prefix.trim();
        return normalized.isEmpty() ? "" : normalized;
    }

    private DataSourceVO toVO(DataResourceEntity entity) {
        DataSourceConnectionConfig config = connectionConfigCipher.decrypt(entity.getConnConfig());

        DataSourceConnectionConfigVO configVO = new DataSourceConnectionConfigVO();
        configVO.setHost(config.getHost());
        configVO.setPort(config.getPort());
        configVO.setDatabase(config.getDatabase());
        configVO.setUsername(config.getUsername());
        configVO.setPasswordMasked(connectionConfigCipher.maskPassword(entity.getConnConfig()));
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
