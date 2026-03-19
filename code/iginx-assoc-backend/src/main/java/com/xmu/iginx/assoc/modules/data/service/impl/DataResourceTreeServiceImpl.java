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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据资源树构建服务实现。
 * <p>
 * 统一按 IGinX 路径前缀构建资源树，ts/rt 仅作为根节点前缀。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class DataResourceTreeServiceImpl implements DataResourceTreeService {

    /** 时序数据前缀 */
    private static final String TS_PREFIX = DataPrefixRules.TS_PREFIX;
    /** 结构化数据前缀 */
    private static final String RT_PREFIX = DataPrefixRules.RT_PREFIX;

    private final DataResourceRepository dataResourceRepository;
    private final IginxStorageWrapper iginxStorageWrapper;

    /**
     * 构建数据资源树。
     * <p>
     * 规则：
     * <ul>
     *     <li>只返回 ts/rt 两个根节点</li>
     * </ul>
     * </p>
     *
     * @return 资源树根节点列表
     */
    @Override
    public List<DataResourceTreeNodeVO> buildTree() {
        // 读取已注册数据源，用于为根节点绑定默认数据源 ID
        List<DataResourceEntity> sources = dataResourceRepository.findAll();
        Long tsSourceId = resolveDefaultSourceId(sources, DataSourceType.IOTDB, DataSourceType.INFLUXDB);
        Long rtSourceId = resolveDefaultSourceId(sources, DataSourceType.POSTGRESQL);

        // 读取 IginX 中当前的路径列表
        List<Column> columns = iginxStorageWrapper.executeWithSession(session -> session.showColumns());
        if (columns == null) {
            columns = List.of();
        }
        // 用于快速复用节点，避免重复创建
        Map<String, DataResourceTreeNodeVO> nodeMap = new LinkedHashMap<>();

        // 构建 ts/rt 两个根节点
        DataResourceTreeNodeVO tsRoot = createRoot(TS_PREFIX, tsSourceId);
        DataResourceTreeNodeVO rtRoot = createRoot(RT_PREFIX, rtSourceId);
        Map<String, DataResourceTreeNodeVO> rootsByPrefix = new LinkedHashMap<>();
        rootsByPrefix.put(TS_PREFIX.toLowerCase(Locale.ROOT), tsRoot);
        rootsByPrefix.put(RT_PREFIX.toLowerCase(Locale.ROOT), rtRoot);

        for (Column column : columns) {
            if (column == null) {
                continue;
            }
            List<String> segments = splitSegments(column.getPath());
            if (segments.size() < 2) {
                continue;
            }
            String prefix = segments.get(0);
            if (!StringUtils.hasText(prefix)) {
                continue;
            }
            DataResourceTreeNodeVO root = rootsByPrefix.get(prefix.toLowerCase(Locale.ROOT));
            if (root == null) {
                continue;
            }
            String lastSegment = segments.get(segments.size() - 1);
            if (IginxStructuredUtils.isInternalKey(lastSegment)) {
                continue;
            }
            appendPathSegments(segments, root, nodeMap, root.getSourceId());
        }

        List<DataResourceTreeNodeVO> roots = new ArrayList<>();
        roots.add(tsRoot);
        roots.add(rtRoot);
        return roots;
    }

    /**
     * 创建根节点。
     *
     * @param prefix 根节点前缀（ts/rt）
     * @param sourceId 默认数据源 ID
     * @return 根节点
     */
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

    /**
     * 追加路径到树中，按路径段逐级展开。
     *
     * @param segments 路径段列表
     * @param root 根节点
     * @param nodeMap 节点缓存
     * @param sourceId 数据源 ID
     */
    private void appendPathSegments(List<String> segments,
                                    DataResourceTreeNodeVO root,
                                    Map<String, DataResourceTreeNodeVO> nodeMap,
                                    Long sourceId) {
        if (segments == null || segments.size() < 2) {
            return;
        }
        DataResourceTreeNodeVO parent = root;
        String currentPath = segments.get(0);
        for (int i = 1; i < segments.size(); i++) {
            currentPath = currentPath + "." + segments.get(i);
            boolean isLeaf = (i == segments.size() - 1);
            String nodeType = isLeaf ? "point" : "group";
            DataResourceTreeNodeVO node = ensureNode(nodeMap, parent, currentPath, segments.get(i), nodeType);
            if (node.getSourceId() == null) {
                node.setSourceId(sourceId);
            }
            node.setPath(currentPath);
            parent = node;
        }
    }

    /**
     * 获取或创建树节点。
     *
     * @param nodeMap 节点缓存
     * @param parent 父节点
     * @param path 节点路径
     * @param name 节点名称
     * @param type 节点类型
     * @return 节点
     */
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

    /**
     * 选取默认数据源 ID（取满足类型的最小 ID）。
     *
     * @param sources 数据源列表
     * @param allowed 允许的数据源类型
     * @return 默认数据源 ID
     */
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

    /**
     * 解析数据源类型。
     *
     * @param raw 原始字符串
     * @return 数据源类型枚举
     */
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

    /**
     * 判断类型是否在允许范围内。
     *
     * @param type 数据源类型
     * @param allowed 允许范围
     * @return 是否允许
     */
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

    /**
     * 将路径拆分为段。
     *
     * @param path 原始路径
     * @return 段列表
     */
    private List<String> splitSegments(String path) {
        if (!StringUtils.hasText(path)) {
            return List.of();
        }
        String normalized = TimeSeriesPathUtils.normalizePath(path);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        List<String> rawSegments = IginxStructuredUtils.splitPathSegments(normalized);
        List<String> segments = new ArrayList<>();
        for (String part : rawSegments) {
            if (StringUtils.hasText(part)) {
                segments.add(part.trim());
            }
        }
        return segments;
    }
}
