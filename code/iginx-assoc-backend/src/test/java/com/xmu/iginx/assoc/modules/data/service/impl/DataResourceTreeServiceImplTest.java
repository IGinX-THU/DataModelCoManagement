package com.xmu.iginx.assoc.modules.data.service.impl;

import cn.edu.tsinghua.iginx.session.Column;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.repository.DataResourceRepository;
import com.xmu.iginx.assoc.modules.data.vo.DataResourceTreeNodeVO;
import com.xmu.iginx.assoc.modules.task.entity.TaskEntity;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionBinding;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionSnapshot;
import com.xmu.iginx.assoc.modules.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 数据资源树构建测试。
 */
@ExtendWith(MockitoExtension.class)
class DataResourceTreeServiceImplTest {

    @Mock
    private DataResourceRepository dataResourceRepository;

    @Mock
    private IginxStorageWrapper iginxStorageWrapper;

    @Mock
    private TaskRepository taskRepository;

    private ObjectMapper objectMapper;
    private DataResourceTreeServiceImpl dataResourceTreeService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        dataResourceTreeService = new DataResourceTreeServiceImpl(
            dataResourceRepository,
            iginxStorageWrapper,
            taskRepository,
            objectMapper
        );
    }

    /**
     * task.result.* 应出现在资源树中，并携带正确的预览语义。
     */
    @Test
    void buildTree_shouldExposeTaskResultPreviewMetadata() throws Exception {
        when(dataResourceRepository.findAll()).thenReturn(List.of());
        when(taskRepository.findAll()).thenReturn(List.of(
            buildTask("taskTs", "TS"),
            buildTask("taskRt", "RT")
        ));

        Column timeSeriesColumn = mock(Column.class);
        when(timeSeriesColumn.getPath()).thenReturn("task.result.taskTs.score");
        Column structuredColumn = mock(Column.class);
        when(structuredColumn.getPath()).thenReturn("task.result.taskRt.power");
        when(iginxStorageWrapper.executeWithSession(any()))
            .thenReturn(List.of(timeSeriesColumn, structuredColumn));

        List<DataResourceTreeNodeVO> roots = dataResourceTreeService.buildTree();

        assertEquals(3, roots.size());
        DataResourceTreeNodeVO taskRoot = roots.stream()
            .filter(node -> "task".equals(node.getType()))
            .findFirst()
            .orElse(null);
        assertNotNull(taskRoot);
        assertEquals(Boolean.TRUE, taskRoot.getReadOnly());

        DataResourceTreeNodeVO timeSeriesLeaf = findNode(taskRoot, "task.result.taskTs.score");
        assertNotNull(timeSeriesLeaf);
        assertEquals("TIME_SERIES", timeSeriesLeaf.getPreviewMode());
        assertEquals("POINT", timeSeriesLeaf.getPreviewRole());

        DataResourceTreeNodeVO structuredTable = findNode(taskRoot, "task.result.taskRt");
        assertNotNull(structuredTable);
        assertEquals("STRUCTURED", structuredTable.getPreviewMode());
        assertEquals("TABLE", structuredTable.getPreviewRole());

        DataResourceTreeNodeVO structuredLeaf = findNode(taskRoot, "task.result.taskRt.power");
        assertNotNull(structuredLeaf);
        assertEquals("STRUCTURED", structuredLeaf.getPreviewMode());
        assertEquals("COLUMN", structuredLeaf.getPreviewRole());
        assertTrue(Boolean.TRUE.equals(structuredLeaf.getReadOnly()));
    }

    /**
     * 构造带执行快照的任务实体。
     */
    private TaskEntity buildTask(String taskId, String inputPathKind) throws Exception {
        TaskExecutionBinding input = new TaskExecutionBinding();
        input.setPathKind(inputPathKind);

        TaskExecutionSnapshot snapshot = new TaskExecutionSnapshot();
        snapshot.setInputs(List.of(input));
        snapshot.setRequiresTimeRange("TS".equalsIgnoreCase(inputPathKind));

        TaskEntity entity = new TaskEntity();
        entity.setId(taskId);
        entity.setExecutionSnapshot(objectMapper.writeValueAsString(snapshot));
        return entity;
    }

    /**
     * 递归查找树节点。
     */
    private DataResourceTreeNodeVO findNode(DataResourceTreeNodeVO root, String nodeId) {
        if (root == null || nodeId == null) {
            return null;
        }
        if (nodeId.equals(root.getId())) {
            return root;
        }
        if (root.getChildren() == null || root.getChildren().isEmpty()) {
            return null;
        }
        for (DataResourceTreeNodeVO child : root.getChildren()) {
            DataResourceTreeNodeVO found = findNode(child, nodeId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
