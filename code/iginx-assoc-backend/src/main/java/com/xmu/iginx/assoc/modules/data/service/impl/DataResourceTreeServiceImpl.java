package com.xmu.iginx.assoc.modules.data.service.impl;

import cn.edu.tsinghua.iginx.session.Column;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.entity.DataResourceEntity;
import com.xmu.iginx.assoc.modules.data.enums.DataSourceType;
import com.xmu.iginx.assoc.modules.data.repository.DataResourceRepository;
import com.xmu.iginx.assoc.modules.data.service.DataResourceTreeService;
import com.xmu.iginx.assoc.modules.data.util.DataPrefixRules;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredUtils;
import com.xmu.iginx.assoc.modules.data.util.TimeSeriesPathUtils;
import com.xmu.iginx.assoc.modules.data.vo.DataResourceTreeNodeVO;
import com.xmu.iginx.assoc.modules.task.entity.TaskEntity;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionBinding;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionSnapshot;
import com.xmu.iginx.assoc.modules.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 数据资源树构建服务实现。
 * <p>
 * 统一按 IGinX 路径前缀构建资源树，ts / rt / task 作为根节点前缀。
 * 其中 task.result.* 需要结合任务执行快照恢复“时序 / 结构化”预览语义。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class DataResourceTreeServiceImpl implements DataResourceTreeService {

    /** 时序数据前缀 */
    private static final String TS_PREFIX = DataPrefixRules.TS_PREFIX;
    /** 结构化数据前缀 */
    private static final String RT_PREFIX = DataPrefixRules.RT_PREFIX;
    /** 任务结果前缀 */
    private static final String TASK_PREFIX = "task";
    /** 任务结果固定二级路径 */
    private static final String TASK_RESULT_SEGMENT = "result";

    /** 时序预览模式 */
    private static final String PREVIEW_TIME_SERIES = "TIME_SERIES";
    /** 结构化预览模式 */
    private static final String PREVIEW_STRUCTURED = "STRUCTURED";
    /** 结构化结果表节点 */
    private static final String PREVIEW_ROLE_TABLE = "TABLE";
    /** 结构化结果列节点 */
    private static final String PREVIEW_ROLE_COLUMN = "COLUMN";
    /** 时序结果测点节点 */
    private static final String PREVIEW_ROLE_POINT = "POINT";
    /** 资源树中需要隐藏的时序时间列别名 */
    private static final Set<String> HIDDEN_TS_TIME_KEY_SEGMENTS = Set.of(
        "time",
        "mytime",
        "timestamp",
        "datetime",
        "eventtime",
        "collecttime",
        "sampletime",
        "measuretime",
        "recordtime",
        "createtime",
        "updatetime",
        "starttime",
        "endtime",
        "timekey",
        "时间",
        "时间戳",
        "采集时间",
        "日期时间"
    );

    private final DataResourceRepository dataResourceRepository;
    private final IginxStorageWrapper iginxStorageWrapper;
    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    /**
     * 构建数据资源树。
     *
     * @return 资源树根节点列表
     */
    @Override
    public List<DataResourceTreeNodeVO> buildTree() {
        // 读取已注册数据源，用于为 ts / rt 根节点绑定默认数据源 ID。
        List<DataResourceEntity> sources = dataResourceRepository.findAll();
        Long tsSourceId = resolveDefaultSourceId(sources, DataSourceType.IOTDB, DataSourceType.INFLUXDB);
        Long rtSourceId = resolveDefaultSourceId(sources, DataSourceType.POSTGRESQL);
        Map<String, TaskPreviewMetadata> taskPreviewMap = loadTaskPreviewMetadata();

        // 读取 IginX 中当前的路径列表。
        List<Column> columns = iginxStorageWrapper.executeWithSession(session -> session.showColumns());
        if (columns == null) {
            columns = List.of();
        }

        // 用于快速复用节点，避免重复创建。
        Map<String, DataResourceTreeNodeVO> nodeMap = new LinkedHashMap<>();

        // 构建 ts / rt / task 三个根节点。
        DataResourceTreeNodeVO tsRoot = createRoot(TS_PREFIX, tsSourceId);
        DataResourceTreeNodeVO rtRoot = createRoot(RT_PREFIX, rtSourceId);
        DataResourceTreeNodeVO taskRoot = createRoot(TASK_PREFIX, null);
        Map<String, DataResourceTreeNodeVO> rootsByPrefix = new LinkedHashMap<>();
        rootsByPrefix.put(TS_PREFIX.toLowerCase(Locale.ROOT), tsRoot);
        rootsByPrefix.put(RT_PREFIX.toLowerCase(Locale.ROOT), rtRoot);
        rootsByPrefix.put(TASK_PREFIX.toLowerCase(Locale.ROOT), taskRoot);

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
            if (shouldHideTimeKeyLeaf(segments, root.getType())) {
                continue;
            }
            appendPathSegments(
                segments,
                root,
                nodeMap,
                root.getSourceId(),
                resolveTaskPreviewMetadata(segments, taskPreviewMap)
            );
        }

        List<DataResourceTreeNodeVO> roots = new ArrayList<>();
        roots.add(tsRoot);
        roots.add(rtRoot);
        roots.add(taskRoot);
        return roots;
    }

    /**
     * 创建根节点。
     *
     * @param prefix 根节点前缀（ts / rt / task）
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
        root.setReadOnly(TASK_PREFIX.equalsIgnoreCase(prefix));
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
     * @param taskPreviewMetadata 任务结果预览元数据
     */
    private void appendPathSegments(List<String> segments,
                                    DataResourceTreeNodeVO root,
                                    Map<String, DataResourceTreeNodeVO> nodeMap,
                                    Long sourceId,
                                    TaskPreviewMetadata taskPreviewMetadata) {
        if (segments == null || segments.size() < 2) {
            return;
        }
        DataResourceTreeNodeVO parent = root;
        String currentPath = segments.get(0);
        for (int index = 1; index < segments.size(); index++) {
            currentPath = currentPath + "." + segments.get(index);
            boolean isLeaf = (index == segments.size() - 1);
            String nodeType = isLeaf ? "point" : "group";
            DataResourceTreeNodeVO node = ensureNode(nodeMap, parent, currentPath, segments.get(index), nodeType);
            if (node.getSourceId() == null) {
                node.setSourceId(sourceId);
            }
            node.setPath(currentPath);
            if (TASK_PREFIX.equalsIgnoreCase(root.getType())) {
                node.setReadOnly(Boolean.TRUE);
            }
            applyTaskPreviewMetadata(node, index, isLeaf, taskPreviewMetadata);
            parent = node;
        }
    }

    /**
     * 将任务结果的预览语义挂载到资源树节点。
     *
     * @param node 当前节点
     * @param segmentIndex 当前路径段索引
     * @param isLeaf 是否叶子节点
     * @param taskPreviewMetadata 任务预览元数据
     */
    private void applyTaskPreviewMetadata(DataResourceTreeNodeVO node,
                                          int segmentIndex,
                                          boolean isLeaf,
                                          TaskPreviewMetadata taskPreviewMetadata) {
        if (node == null || taskPreviewMetadata == null || !StringUtils.hasText(taskPreviewMetadata.previewMode())) {
            return;
        }
        // task / task.result 属于公共目录，不绑定具体任务的展示语义。
        if (segmentIndex < 2) {
            return;
        }
        node.setPreviewMode(taskPreviewMetadata.previewMode());
        if (PREVIEW_STRUCTURED.equals(taskPreviewMetadata.previewMode())) {
            if (segmentIndex == 2) {
                node.setPreviewRole(PREVIEW_ROLE_TABLE);
            } else if (isLeaf) {
                node.setPreviewRole(PREVIEW_ROLE_COLUMN);
            }
            return;
        }
        if (isLeaf) {
            node.setPreviewRole(PREVIEW_ROLE_POINT);
        }
    }

    /**
     * 解析当前 task 路径所属任务的预览元数据。
     *
     * @param segments 路径段
     * @param taskPreviewMap taskId -> 预览元数据
     * @return 预览元数据；非任务结果路径返回 null
     */
    private TaskPreviewMetadata resolveTaskPreviewMetadata(List<String> segments,
                                                           Map<String, TaskPreviewMetadata> taskPreviewMap) {
        if (segments == null || segments.size() < 4 || taskPreviewMap == null || taskPreviewMap.isEmpty()) {
            return null;
        }
        if (!TASK_PREFIX.equalsIgnoreCase(segments.get(0))
            || !TASK_RESULT_SEGMENT.equalsIgnoreCase(segments.get(1))) {
            return null;
        }
        String taskId = segments.get(2);
        if (!StringUtils.hasText(taskId)) {
            return null;
        }
        return taskPreviewMap.get(taskId.trim());
    }

    /**
     * 读取任务预览模式映射。
     * <p>
     * task.result.* 本身不携带 ts / rt 前缀，因此要结合任务执行快照恢复真实展示模式。
     * </p>
     *
     * @return taskId -> 预览元数据
     */
    private Map<String, TaskPreviewMetadata> loadTaskPreviewMetadata() {
        List<TaskEntity> tasks = taskRepository.findAll();
        if (tasks == null || tasks.isEmpty()) {
            return Map.of();
        }
        Map<String, TaskPreviewMetadata> result = new LinkedHashMap<>();
        for (TaskEntity task : tasks) {
            if (task == null || !StringUtils.hasText(task.getId())) {
                continue;
            }
            String previewMode = resolveTaskPreviewMode(task);
            if (!StringUtils.hasText(previewMode)) {
                continue;
            }
            result.put(task.getId().trim(), new TaskPreviewMetadata(previewMode));
        }
        return result;
    }

    /**
     * 从任务实体恢复预览模式。
     *
     * @param task 任务实体
     * @return 预览模式
     */
    private String resolveTaskPreviewMode(TaskEntity task) {
        if (task == null) {
            return "";
        }
        if (StringUtils.hasText(task.getExecutionSnapshot())) {
            try {
                TaskExecutionSnapshot snapshot = objectMapper.readValue(task.getExecutionSnapshot(), TaskExecutionSnapshot.class);
                String previewMode = resolvePreviewModeFromSnapshot(snapshot);
                if (StringUtils.hasText(previewMode)) {
                    return previewMode;
                }
            } catch (Exception ignored) {
            }
        }
        return task.getRangeStart() != null || task.getRangeEnd() != null
            ? PREVIEW_TIME_SERIES
            : PREVIEW_STRUCTURED;
    }

    /**
     * 从执行快照中推断展示模式。
     * <p>
     * 规则与任务列表接口保持一致：只要输入中有 ts 路径，就按时序展示；否则按结构化展示。
     * </p>
     *
     * @param snapshot 执行快照
     * @return 展示模式
     */
    private String resolvePreviewModeFromSnapshot(TaskExecutionSnapshot snapshot) {
        if (snapshot == null || snapshot.getInputs() == null || snapshot.getInputs().isEmpty()) {
            return "";
        }
        boolean hasTs = snapshot.getInputs().stream()
            .filter(Objects::nonNull)
            .map(TaskExecutionBinding::getPathKind)
            .anyMatch(pathKind -> "TS".equalsIgnoreCase(pathKind));
        if (hasTs) {
            return PREVIEW_TIME_SERIES;
        }
        boolean hasRt = snapshot.getInputs().stream()
            .filter(Objects::nonNull)
            .map(TaskExecutionBinding::getPathKind)
            .anyMatch(pathKind -> "RT".equalsIgnoreCase(pathKind));
        if (hasRt) {
            return PREVIEW_STRUCTURED;
        }
        if (Boolean.TRUE.equals(snapshot.getRequiresTimeRange())) {
            return PREVIEW_TIME_SERIES;
        }
        return "";
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
     * 判断当前叶子路径是否属于应从时序资源树中隐藏的时间列。
     * <p>
     * 说明：IGinX 在 CSV 导入时即使某列被指定为 KEY，showColumns 里仍可能出现同名叶子路径。
     * 为避免用户在资源树里再去点开这类“时间测点”，这里在 ts 根节点下按常见时间列别名做过滤。
     * </p>
     *
     * @param segments 路径段列表
     * @param rootType 根节点类型
     * @return 是否需要隐藏
     */
    private boolean shouldHideTimeKeyLeaf(List<String> segments, String rootType) {
        if (!TS_PREFIX.equalsIgnoreCase(rootType) || segments == null || segments.size() < 2) {
            return false;
        }
        String leafSegment = segments.get(segments.size() - 1);
        if (!StringUtils.hasText(leafSegment)) {
            return false;
        }
        return HIDDEN_TS_TIME_KEY_SEGMENTS.contains(normalizeLeafSegment(leafSegment));
    }

    /**
     * 规范化叶子段名称，便于兼容 my_time / my-time / my time 等写法。
     *
     * @param raw 原始叶子段
     * @return 规范化后的名称
     */
    private String normalizeLeafSegment(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim()
            .toLowerCase(Locale.ROOT)
            .replace("_", "")
            .replace("-", "")
            .replace(" ", "");
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

    /**
     * 任务预览元数据。
     *
     * @param previewMode 预览模式
     */
    private record TaskPreviewMetadata(String previewMode) {
    }
}
