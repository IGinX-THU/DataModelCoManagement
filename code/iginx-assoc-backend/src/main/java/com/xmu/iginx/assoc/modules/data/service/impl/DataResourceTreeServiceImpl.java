package com.xmu.iginx.assoc.modules.data.service.impl;

import cn.edu.tsinghua.iginx.session.Column;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.entity.DataResourceEntity;
import com.xmu.iginx.assoc.modules.data.enums.DataSourceType;
import com.xmu.iginx.assoc.modules.data.repository.DataResourceRepository;
import com.xmu.iginx.assoc.modules.data.service.DataResourceTreeService;
import com.xmu.iginx.assoc.modules.data.util.DataPrefixRules;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredUtils;
import com.xmu.iginx.assoc.modules.data.util.TimeSeriesPathUtils;
import com.xmu.iginx.assoc.modules.data.vo.DataResourceTreeNodeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 数据资源树构建服务实现。
 */
@Service
@RequiredArgsConstructor
public class DataResourceTreeServiceImpl implements DataResourceTreeService {

    private static final String TS_PREFIX = DataPrefixRules.TS_PREFIX;
    private static final String RT_PREFIX = DataPrefixRules.RT_PREFIX;
    private static final String MODEL_PREFIX = DataPrefixRules.MODEL_PREFIX;

    private final DataResourceRepository dataResourceRepository;
    private final IginxStorageWrapper iginxStorageWrapper;

    @Override
    public List<DataResourceTreeNodeVO> buildTree() {
        List<DataResourceEntity> sources = dataResourceRepository.findAll();
        Long tsSourceId = resolveDefaultSourceId(sources, DataSourceType.IOTDB, DataSourceType.INFLUXDB);
        Long rtSourceId = resolveDefaultSourceId(sources, DataSourceType.POSTGRESQL);

        List<Column> columns = iginxStorageWrapper.executeWithSession(session -> session.showColumns());
        if (columns == null) {
            columns = List.of();
        }
        Map<String, DataResourceTreeNodeVO> nodeMap = new LinkedHashMap<>();

        DataResourceTreeNodeVO tsRoot = createRoot(TS_PREFIX, tsSourceId);
        DataResourceTreeNodeVO rtRoot = createRoot(RT_PREFIX, rtSourceId);
        DataResourceTreeNodeVO modelRoot = createRoot(MODEL_PREFIX, null);

        Set<String> structuredTables = new LinkedHashSet<>();

        for (Column column : columns) {
            if (column == null || column.getPath() == null) {
                continue;
            }
            String normalized = normalizeFullPath(column.getPath());
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (DataPrefixRules.startsWithPrefix(normalized, TS_PREFIX)) {
                addTimeSeriesPath(normalized, tsRoot, nodeMap, tsSourceId);
                continue;
            }
            if (DataPrefixRules.startsWithPrefix(normalized, MODEL_PREFIX)) {
                addModelPath(normalized, modelRoot, nodeMap);
                continue;
            }
            if (DataPrefixRules.startsWithPrefix(normalized, RT_PREFIX)) {
                addStructuredPath(normalized, rtRoot, nodeMap, rtSourceId, structuredTables);
            }
        }

        List<DataResourceTreeNodeVO> roots = new ArrayList<>();
        roots.add(tsRoot);
        roots.add(rtRoot);
        roots.add(modelRoot);
        return roots;
    }

    private DataResourceTreeNodeVO createRoot(String prefix, Long sourceId) {
        DataResourceTreeNodeVO root = new DataResourceTreeNodeVO();
        root.setId(prefix);
        root.setName(prefix);
        root.setType(prefix);
        root.setPath(prefix);
        root.setSourceId(sourceId);
        root.setChildren(new ArrayList<>());
        return root;
    }

    private void addTimeSeriesPath(String normalizedPath,
                                   DataResourceTreeNodeVO root,
                                   Map<String, DataResourceTreeNodeVO> nodeMap,
                                   Long sourceId) {
        List<String> segments = splitSegments(normalizedPath);
        if (segments.size() < 2) {
            return;
        }
        DataResourceTreeNodeVO parent = root;
        String currentPath = segments.get(0);
        for (int i = 1; i < segments.size(); i++) {
            currentPath = currentPath + "." + segments.get(i);
            String nodeType = (i == segments.size() - 1) ? "point" : "group";
            DataResourceTreeNodeVO node = ensureNode(nodeMap, parent, currentPath, segments.get(i), nodeType);
            if (node.getSourceId() == null) {
                node.setSourceId(sourceId);
            }
            node.setPath(currentPath);
            if ("group".equals(nodeType)) {
                node.setType("group");
            }
            parent = node;
        }
    }

    private void addModelPath(String normalizedPath,
                              DataResourceTreeNodeVO root,
                              Map<String, DataResourceTreeNodeVO> nodeMap) {
        List<String> segments = splitSegments(normalizedPath);
        if (segments.size() < 2) {
            return;
        }
        DataResourceTreeNodeVO parent = root;
        String currentPath = segments.get(0);
        for (int i = 1; i < segments.size(); i++) {
            currentPath = currentPath + "." + segments.get(i);
            String nodeType = (i == segments.size() - 1) ? "file" : "group";
            DataResourceTreeNodeVO node = ensureNode(nodeMap, parent, currentPath, segments.get(i), nodeType);
            node.setPath(currentPath);
            parent = node;
        }
    }

    private void addStructuredPath(String normalizedPath,
                                   DataResourceTreeNodeVO root,
                                   Map<String, DataResourceTreeNodeVO> nodeMap,
                                   Long sourceId,
                                   Set<String> structuredTables) {
        List<String> segments = IginxStructuredUtils.splitPathSegments(normalizedPath);
        if (segments.size() < 2) {
            return;
        }
        if ("root".equalsIgnoreCase(segments.get(0))) {
            segments = segments.subList(1, segments.size());
        }
        if (segments.isEmpty()) {
            return;
        }
        if (RT_PREFIX.equalsIgnoreCase(segments.get(0))) {
            segments = segments.subList(1, segments.size());
        }
        if (segments.size() < 2) {
            return;
        }
        String columnName = segments.get(segments.size() - 1);
        if (IginxStructuredUtils.isInternalKey(columnName)) {
            return;
        }
        List<String> tableSegments = segments.subList(0, segments.size() - 1);
        if (tableSegments.isEmpty()) {
            return;
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

        String schemaPath = RT_PREFIX + "." + schema;
        String tablePath = schemaPath + "." + table;
        if (!structuredTables.add(tablePath)) {
            return;
        }

        DataResourceTreeNodeVO schemaNode = ensureNode(nodeMap, root, schemaPath, schema, "schema");
        if (schemaNode.getSourceId() == null) {
            schemaNode.setSourceId(sourceId);
        }
        schemaNode.setPath(schemaPath);

        DataResourceTreeNodeVO tableNode = ensureNode(nodeMap, schemaNode, tablePath, table, "table");
        tableNode.setSchema(schema);
        tableNode.setTable(table);
        tableNode.setSourceId(sourceId);
        tableNode.setPath(tablePath);
    }

    private DataResourceTreeNodeVO ensureNode(Map<String, DataResourceTreeNodeVO> nodeMap,
                                              DataResourceTreeNodeVO parent,
                                              String path,
                                              String name,
                                              String type) {
        DataResourceTreeNodeVO node = nodeMap.get(path);
        if (node == null) {
            node = new DataResourceTreeNodeVO();
            node.setId(path);
            node.setName(name);
            node.setType(type);
            node.setChildren(new ArrayList<>());
            nodeMap.put(path, node);
            if (parent != null) {
                parent.getChildren().add(node);
            }
            return node;
        }
        if ("group".equals(type)) {
            node.setType("group");
        } else if (node.getType() == null) {
            node.setType(type);
        }
        return node;
    }

    private String normalizeFullPath(String path) {
        String normalized = TimeSeriesPathUtils.normalizePath(path);
        if (normalized == null) {
            return "";
        }
        if (normalized.toLowerCase(Locale.ROOT).startsWith("root.")) {
            return normalized.substring("root.".length());
        }
        return normalized;
    }

    private Long resolveDefaultSourceId(List<DataResourceEntity> sources, DataSourceType... allowed) {
        if (sources == null || sources.isEmpty() || allowed == null || allowed.length == 0) {
            return null;
        }
        return sources.stream()
            .filter(entity -> entity != null && entity.getId() != null)
            .filter(entity -> isAllowed(parseType(entity.getSourceType()), allowed))
            .map(DataResourceEntity::getId)
            .sorted()
            .findFirst()
            .orElse(null);
    }

    private DataSourceType parseType(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return DataSourceType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isAllowed(DataSourceType type, DataSourceType[] allowed) {
        if (type == null) {
            return false;
        }
        for (DataSourceType candidate : allowed) {
            if (candidate == type) {
                return true;
            }
        }
        return false;
    }

    private List<String> splitSegments(String path) {
        if (!StringUtils.hasText(path)) {
            return List.of();
        }
        String normalized = TimeSeriesPathUtils.normalizePath(path);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        String[] parts = normalized.split("\\.");
        List<String> segments = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                segments.add(part.trim());
            }
        }
        return segments;
    }
}
