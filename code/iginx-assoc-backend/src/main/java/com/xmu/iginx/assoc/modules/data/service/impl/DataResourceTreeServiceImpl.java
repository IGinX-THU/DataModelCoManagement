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
        List<MountBinding> tsBindings = buildBindings(sources, DataSourceType.IOTDB, DataSourceType.INFLUXDB);
        List<MountBinding> rtBindings = buildBindings(sources, DataSourceType.POSTGRESQL);

        List<Column> columns = iginxStorageWrapper.executeWithSession(session -> session.showColumns());
        Map<String, DataResourceTreeNodeVO> nodeMap = new LinkedHashMap<>();

        DataResourceTreeNodeVO tsRoot = createRoot(TS_PREFIX);
        DataResourceTreeNodeVO rtRoot = createRoot(RT_PREFIX);
        DataResourceTreeNodeVO modelRoot = createRoot(MODEL_PREFIX);

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
                addTimeSeriesPath(normalized, tsRoot, nodeMap, tsBindings);
                continue;
            }
            if (DataPrefixRules.startsWithPrefix(normalized, MODEL_PREFIX)) {
                addModelPath(normalized, modelRoot, nodeMap);
                continue;
            }
            if (DataPrefixRules.startsWithPrefix(normalized, RT_PREFIX)) {
                addStructuredPath(normalized, rtRoot, nodeMap, rtBindings, structuredTables);
            }
        }

        List<DataResourceTreeNodeVO> roots = new ArrayList<>();
        roots.add(tsRoot);
        roots.add(rtRoot);
        roots.add(modelRoot);
        return roots;
    }

    private DataResourceTreeNodeVO createRoot(String prefix) {
        DataResourceTreeNodeVO root = new DataResourceTreeNodeVO();
        root.setId(prefix);
        root.setName(prefix);
        root.setType(prefix);
        root.setPath(prefix);
        root.setChildren(new ArrayList<>());
        return root;
    }

    private void addTimeSeriesPath(String normalizedPath,
                                   DataResourceTreeNodeVO root,
                                   Map<String, DataResourceTreeNodeVO> nodeMap,
                                   List<MountBinding> bindings) {
        String path = normalizedPath;
        boolean isInit = path.endsWith(".__init__");
        if (isInit) {
            path = path.substring(0, path.length() - ".__init__".length());
        }
        List<String> segments = splitSegments(path);
        if (segments.size() < 2) {
            return;
        }
        DataResourceTreeNodeVO parent = root;
        String currentPath = segments.get(0);
        for (int i = 1; i < segments.size(); i++) {
            currentPath = currentPath + "." + segments.get(i);
            String nodeType = (i == segments.size() - 1)
                ? (isInit ? "group" : "point")
                : "group";
            DataResourceTreeNodeVO node = ensureNode(nodeMap, parent, currentPath, segments.get(i), nodeType);
            if (node.getSourceId() == null) {
                node.setSourceId(resolveSourceId(currentPath, bindings));
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
                                   List<MountBinding> bindings,
                                   Set<String> structuredTables) {
        List<String> segments = IginxStructuredUtils.splitPathSegments(normalizedPath);
        if (segments.size() < 2) {
            return;
        }
        String columnName = segments.get(segments.size() - 1);
        if (IginxStructuredUtils.isInternalKey(columnName)) {
            return;
        }
        List<String> tableSegments = segments.subList(0, segments.size() - 1);
        String tablePath = String.join(".", tableSegments);
        if (!structuredTables.add(tablePath)) {
            return;
        }
        MountBinding binding = resolveBinding(tableSegments, bindings);
        List<String> mountSegments = binding == null ? List.of(RT_PREFIX) : binding.segments();
        List<String> relativeSegments = resolveRelativeSegments(tableSegments, mountSegments);
        if (relativeSegments.isEmpty()) {
            return;
        }
        String schema;
        String table;
        if (relativeSegments.size() >= 2) {
            schema = relativeSegments.get(0);
            table = String.join(".", relativeSegments.subList(1, relativeSegments.size()));
        } else {
            schema = "public";
            table = relativeSegments.get(0);
        }

        int schemaIndex = resolveSchemaIndex(tableSegments, mountSegments);
        if (schemaIndex < 0 || schemaIndex >= tableSegments.size()) {
            return;
        }
        DataResourceTreeNodeVO parent = root;
        String currentPath = tableSegments.get(0);
        for (int i = 1; i < schemaIndex; i++) {
            currentPath = currentPath + "." + tableSegments.get(i);
            DataResourceTreeNodeVO groupNode = ensureNode(nodeMap, parent, currentPath, tableSegments.get(i), "group");
            if (groupNode.getSourceId() == null) {
                groupNode.setSourceId(resolveSourceId(currentPath, bindings));
            }
            groupNode.setPath(currentPath);
            parent = groupNode;
        }

        String schemaSegment = tableSegments.get(schemaIndex);
        String schemaPath = String.join(".", tableSegments.subList(0, schemaIndex + 1));
        DataResourceTreeNodeVO schemaNode = ensureNode(nodeMap, parent, schemaPath, schemaSegment, "schema");
        if (schemaNode.getSourceId() == null) {
            schemaNode.setSourceId(binding == null ? null : binding.sourceId());
        }
        schemaNode.setPath(schemaPath);

        DataResourceTreeNodeVO tableNode = ensureNode(nodeMap, schemaNode, tablePath, table, "table");
        tableNode.setSchema(schema);
        tableNode.setTable(table);
        tableNode.setMountPath(binding == null ? null : binding.mountPath());
        tableNode.setSourceId(binding == null ? null : binding.sourceId());
        tableNode.setPath(tablePath);
    }

    private int resolveSchemaIndex(List<String> tableSegments, List<String> mountSegments) {
        if (startsWithSegments(tableSegments, mountSegments) && tableSegments.size() > mountSegments.size()) {
            return mountSegments.size();
        }
        if (tableSegments.size() > 1) {
            return 1;
        }
        return -1;
    }

    private List<String> resolveRelativeSegments(List<String> tableSegments, List<String> mountSegments) {
        if (startsWithSegments(tableSegments, mountSegments)) {
            return tableSegments.subList(mountSegments.size(), tableSegments.size());
        }
        if (tableSegments.size() > 1) {
            return tableSegments.subList(1, tableSegments.size());
        }
        return List.of();
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

    private List<MountBinding> buildBindings(List<DataResourceEntity> sources, DataSourceType... allowed) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        List<MountBinding> bindings = new ArrayList<>();
        for (DataResourceEntity entity : sources) {
            DataSourceType type = parseType(entity.getSourceType());
            if (!isAllowed(type, allowed)) {
                continue;
            }
            String mountPath = TimeSeriesPathUtils.normalizePath(entity.getMountPath());
            if (!StringUtils.hasText(mountPath)) {
                continue;
            }
            if (type == DataSourceType.IOTDB || type == DataSourceType.INFLUXDB) {
                mountPath = TimeSeriesPathUtils.stripRootPrefix(mountPath);
            }
            if (!DataPrefixRules.startsWithPrefix(mountPath, type == DataSourceType.POSTGRESQL ? RT_PREFIX : TS_PREFIX)) {
                continue;
            }
            bindings.add(new MountBinding(entity.getId(), mountPath, splitSegments(mountPath)));
        }
        return bindings;
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
        if (type == null || allowed == null || allowed.length == 0) {
            return false;
        }
        for (DataSourceType candidate : allowed) {
            if (candidate == type) {
                return true;
            }
        }
        return false;
    }

    private Long resolveSourceId(String path, List<MountBinding> bindings) {
        if (bindings == null || bindings.isEmpty() || path == null) {
            return null;
        }
        List<String> segments = splitSegments(path);
        MountBinding binding = resolveBinding(segments, bindings);
        return binding == null ? null : binding.sourceId();
    }

    private MountBinding resolveBinding(List<String> pathSegments, List<MountBinding> bindings) {
        MountBinding best = null;
        int bestLen = -1;
        for (MountBinding binding : bindings) {
            if (binding == null || binding.segments() == null) {
                continue;
            }
            if (!startsWithSegments(pathSegments, binding.segments())) {
                continue;
            }
            int len = binding.segments().size();
            if (len > bestLen) {
                best = binding;
                bestLen = len;
            }
        }
        return best;
    }

    private boolean startsWithSegments(List<String> segments, List<String> prefix) {
        if (segments == null || prefix == null || prefix.size() > segments.size()) {
            return false;
        }
        for (int i = 0; i < prefix.size(); i++) {
            if (!prefix.get(i).equalsIgnoreCase(segments.get(i))) {
                return false;
            }
        }
        return true;
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

    private record MountBinding(Long sourceId, String mountPath, List<String> segments) {
    }
}
