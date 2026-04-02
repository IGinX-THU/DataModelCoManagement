package com.xmu.iginx.assoc.modules.task.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.model.entity.ModelAssetEntity;
import com.xmu.iginx.assoc.modules.model.repository.MetaModelProfileRepository;
import com.xmu.iginx.assoc.modules.model.repository.ModelAssetRepository;
import com.xmu.iginx.assoc.modules.model.util.ModelFileStorageService;
import com.xmu.iginx.assoc.modules.model.util.ModelFunctionSchemaParser;
import com.xmu.iginx.assoc.modules.relation.entity.AssociationRuleEntity;
import com.xmu.iginx.assoc.modules.relation.repository.AssociationRuleRepository;
import com.xmu.iginx.assoc.modules.task.dto.TaskSubmitRequest;
import com.xmu.iginx.assoc.modules.task.entity.TaskEntity;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionSnapshot;
import com.xmu.iginx.assoc.modules.task.repository.TaskRepository;
import com.xmu.iginx.assoc.modules.task.service.TaskScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务服务提交流程测试。
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private AssociationRuleRepository associationRuleRepository;

    @Mock
    private ModelAssetRepository modelAssetRepository;

    @Mock
    private MetaModelProfileRepository profileRepository;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private TaskModelExecutionEngine taskModelExecutionEngine;

    @Mock
    private ModelFileStorageService modelFileStorageService;

    @Mock
    private ModelFunctionSchemaParser functionSchemaParser;

    private TaskServiceImpl taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskServiceImpl(
            taskRepository,
            associationRuleRepository,
            modelAssetRepository,
            profileRepository,
            taskScheduler,
            new ObjectMapper(),
            taskModelExecutionEngine,
            modelFileStorageService,
            functionSchemaParser
        );
    }

    /**
     * 输出路径留空且输入为 rt.* 时，可不传时间区间并直接提交任务。
     */
    @Test
    void submitTask_shouldSubmitWithoutTimeRangeWhenInputsAreRt() {
        AssociationRuleEntity rule = new AssociationRuleEntity();
        rule.setId(1L);
        rule.setEnabled(true);
        rule.setModelId(9L);
        rule.setName("规则A");
        rule.setFunctionName("predict");
        rule.setMappingJson("{\"function_name\":\"predict\",\"mappings\":[{\"param\":\"temperature\",\"param_type\":\"FLOAT\",\"direction\":\"INPUT\",\"source_path\":\"rt.factory.demo.temperature\"}],"
            + "\"output_target\":{\"paths\":{\"power\":\"\"}}}");
        rule.setOutputTarget("{\"paths\":{\"power\":\"\"},\"meta\":[{\"param\":\"power\",\"param_type\":\"FLOAT\",\"direction\":\"OUTPUT\"}]}");

        ModelAssetEntity asset = new ModelAssetEntity();
        asset.setId(9L);
        asset.setProfileId(2L);
        asset.setFileType("PY");
        asset.setVersion("v1");
        asset.setIoSchema("{\"inputs\":[{\"name\":\"temperature\",\"type\":\"FLOAT\",\"required\":true}],"
            + "\"outputs\":[{\"name\":\"power\",\"type\":\"FLOAT\"}]}");

        when(associationRuleRepository.findById(1L)).thenReturn(Optional.of(rule));
        when(modelAssetRepository.findById(9L)).thenReturn(Optional.of(asset));
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskSubmitRequest request = new TaskSubmitRequest();
        request.setRuleId(1L);

        String taskId = taskService.submitTask(request);

        assertNotNull(taskId);
        ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository).save(captor.capture());
        TaskEntity saved = captor.getValue();
        assertNotNull(saved.getTaskName());
        assertTrue(saved.getTaskName().startsWith("规则A_"));
        assertNotNull(saved.getResultLink());
        assertTrue(saved.getResultLink().startsWith("task.result."));
        assertNull(saved.getRangeStart());
        assertNull(saved.getRangeEnd());
        verify(taskScheduler).submit(eq(taskId), any(Runnable.class));
    }

    /**
     * 设置计划开始/终止时间后，应进入定时调度流程并持久化调度信息。
     */
    @Test
    void submitTask_shouldScheduleWhenScheduledWindowProvided() {
        AssociationRuleEntity rule = new AssociationRuleEntity();
        rule.setId(2L);
        rule.setEnabled(true);
        rule.setModelId(9L);
        rule.setName("规则Schedule");
        rule.setFunctionName("predict");
        rule.setMappingJson("{\"function_name\":\"predict\",\"mappings\":[{\"param\":\"temperature\",\"param_type\":\"FLOAT\",\"direction\":\"INPUT\",\"source_path\":\"rt.factory.demo.temperature\"}],"
            + "\"output_target\":{\"paths\":{\"power\":\"\"}}}");
        rule.setOutputTarget("{\"paths\":{\"power\":\"\"},\"meta\":[{\"param\":\"power\",\"param_type\":\"FLOAT\",\"direction\":\"OUTPUT\"}]}");

        ModelAssetEntity asset = new ModelAssetEntity();
        asset.setId(9L);
        asset.setProfileId(2L);
        asset.setFileType("PY");
        asset.setVersion("v1");
        asset.setIoSchema("{\"inputs\":[{\"name\":\"temperature\",\"type\":\"FLOAT\",\"required\":true}],"
            + "\"outputs\":[{\"name\":\"power\",\"type\":\"FLOAT\"}]}");

        when(associationRuleRepository.findById(2L)).thenReturn(Optional.of(rule));
        when(modelAssetRepository.findById(9L)).thenReturn(Optional.of(asset));
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime scheduledStartTime = LocalDateTime.now().plusMinutes(10);
        LocalDateTime scheduledEndTime = scheduledStartTime.plusMinutes(20);
        TaskSubmitRequest request = new TaskSubmitRequest();
        request.setRuleId(2L);
        request.setScheduledStartTime(scheduledStartTime);
        request.setScheduledEndTime(scheduledEndTime);

        String taskId = taskService.submitTask(request);

        ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository).save(captor.capture());
        TaskEntity saved = captor.getValue();
        assertEquals(scheduledStartTime, saved.getScheduledStartTime());
        assertEquals(scheduledEndTime, saved.getScheduledEndTime());
        assertTrue(saved.getExecLog().contains("将于"));
        verify(taskScheduler).schedule(eq(taskId), any(Runnable.class), eq(scheduledStartTime), any());
        verify(taskScheduler).scheduleDeadline(eq(taskId), eq(scheduledEndTime), any(Runnable.class));
    }

    /**
     * 用户手动输入任务名称时，应按输入名称保存，避免被默认名称覆盖。
     */
    @Test
    void submitTask_shouldUseCustomTaskNameWhenProvided() {
        AssociationRuleEntity rule = new AssociationRuleEntity();
        rule.setId(12L);
        rule.setEnabled(true);
        rule.setModelId(9L);
        rule.setName("规则自定义任务名");
        rule.setFunctionName("predict");
        rule.setMappingJson("{\"function_name\":\"predict\",\"mappings\":[{\"param\":\"temperature\",\"param_type\":\"FLOAT\",\"direction\":\"INPUT\",\"source_path\":\"rt.factory.demo.temperature\"}],"
            + "\"output_target\":{\"paths\":{\"power\":\"\"}}}");
        rule.setOutputTarget("{\"paths\":{\"power\":\"\"},\"meta\":[{\"param\":\"power\",\"param_type\":\"FLOAT\",\"direction\":\"OUTPUT\"}]}");

        ModelAssetEntity asset = new ModelAssetEntity();
        asset.setId(9L);
        asset.setProfileId(2L);
        asset.setFileType("PY");
        asset.setVersion("v1");
        asset.setIoSchema("{\"inputs\":[{\"name\":\"temperature\",\"type\":\"FLOAT\",\"required\":true}],"
            + "\"outputs\":[{\"name\":\"power\",\"type\":\"FLOAT\"}]}");

        when(associationRuleRepository.findById(12L)).thenReturn(Optional.of(rule));
        when(modelAssetRepository.findById(9L)).thenReturn(Optional.of(asset));
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskSubmitRequest request = new TaskSubmitRequest();
        request.setRuleId(12L);
        request.setTaskName("锅炉功率预测-晚高峰");

        taskService.submitTask(request);

        ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository).save(captor.capture());
        TaskEntity saved = captor.getValue();
        assertEquals("锅炉功率预测-晚高峰", saved.getTaskName());
    }

    /**
     * 若输入包含 ts.*，提交任务时必须提供时间区间。
     */
    @Test
    void submitTask_shouldRejectWhenTsInputMissingTimeRange() {
        AssociationRuleEntity rule = new AssociationRuleEntity();
        rule.setId(1L);
        rule.setEnabled(true);
        rule.setModelId(9L);
        rule.setName("规则TS");
        rule.setFunctionName("predict");
        rule.setMappingJson("{\"function_name\":\"predict\",\"mappings\":[{\"param\":\"temperature\",\"param_type\":\"FLOAT\",\"direction\":\"INPUT\",\"source_path\":\"ts.factory.demo.temperature\"}],"
            + "\"output_target\":{\"paths\":{\"power\":\"\"}}}");
        rule.setOutputTarget("{\"paths\":{\"power\":\"\"},\"meta\":[{\"param\":\"power\",\"param_type\":\"FLOAT\",\"direction\":\"OUTPUT\"}]}");

        ModelAssetEntity asset = new ModelAssetEntity();
        asset.setId(9L);
        asset.setProfileId(2L);
        asset.setFileType("PY");
        asset.setVersion("v1");
        asset.setIoSchema("{\"inputs\":[{\"name\":\"temperature\",\"type\":\"FLOAT\",\"required\":true}],"
            + "\"outputs\":[{\"name\":\"power\",\"type\":\"FLOAT\"}]}");

        when(associationRuleRepository.findById(1L)).thenReturn(Optional.of(rule));
        when(modelAssetRepository.findById(9L)).thenReturn(Optional.of(asset));

        TaskSubmitRequest request = new TaskSubmitRequest();
        request.setRuleId(1L);

        BizException ex = assertThrows(BizException.class, () -> taskService.submitTask(request));
        assertTrue(ex.getMessage().contains("必须选择时间区间"));
        verify(taskRepository, never()).save(any(TaskEntity.class));
        verify(taskScheduler, never()).submit(any(String.class), any(Runnable.class));
    }

    /**
     * 若终止时间不晚于当前生效开始时间，应拒绝提交。
     */
    @Test
    void submitTask_shouldRejectWhenScheduledEndTimeIsInvalid() {
        AssociationRuleEntity rule = new AssociationRuleEntity();
        rule.setId(3L);
        rule.setEnabled(true);
        rule.setModelId(9L);
        rule.setName("规则InvalidSchedule");
        rule.setFunctionName("predict");
        rule.setMappingJson("{\"function_name\":\"predict\",\"mappings\":[{\"param\":\"temperature\",\"param_type\":\"FLOAT\",\"direction\":\"INPUT\",\"source_path\":\"rt.factory.demo.temperature\"}],"
            + "\"output_target\":{\"paths\":{\"power\":\"\"}}}");
        rule.setOutputTarget("{\"paths\":{\"power\":\"\"},\"meta\":[{\"param\":\"power\",\"param_type\":\"FLOAT\",\"direction\":\"OUTPUT\"}]}");

        ModelAssetEntity asset = new ModelAssetEntity();
        asset.setId(9L);
        asset.setProfileId(2L);
        asset.setFileType("PY");
        asset.setVersion("v1");
        asset.setIoSchema("{\"inputs\":[{\"name\":\"temperature\",\"type\":\"FLOAT\",\"required\":true}],"
            + "\"outputs\":[{\"name\":\"power\",\"type\":\"FLOAT\"}]}");

        when(associationRuleRepository.findById(3L)).thenReturn(Optional.of(rule));
        when(modelAssetRepository.findById(9L)).thenReturn(Optional.of(asset));

        TaskSubmitRequest request = new TaskSubmitRequest();
        request.setRuleId(3L);
        request.setScheduledEndTime(LocalDateTime.now().minusMinutes(1));

        BizException ex = assertThrows(BizException.class, () -> taskService.submitTask(request));
        assertTrue(ex.getMessage().contains("任务终止时间必须晚于当前时间"));
        verify(taskRepository, never()).save(any(TaskEntity.class));
        verify(taskScheduler, never()).schedule(any(String.class), any(Runnable.class), any(LocalDateTime.class), any());
        verify(taskScheduler, never()).scheduleDeadline(any(String.class), any(LocalDateTime.class), any(Runnable.class));
    }

    /**
     * 任务提交前应完成严格参数校验：输出参数集合不一致时禁止提交。
     */
    @Test
    void submitTask_shouldRejectWhenOutputParamsNotMatchSchema() {
        AssociationRuleEntity rule = new AssociationRuleEntity();
        rule.setId(1L);
        rule.setEnabled(true);
        rule.setModelId(9L);
        rule.setName("规则B");
        rule.setFunctionName("predict");
        rule.setMappingJson("{\"function_name\":\"predict\",\"mappings\":[{\"param\":\"temperature\",\"param_type\":\"FLOAT\",\"direction\":\"INPUT\",\"source_path\":\"rt.factory.demo.temperature\"}],"
            + "\"output_target\":{\"paths\":{\"wrong\":\"\"}}}");
        rule.setOutputTarget("{\"paths\":{\"wrong\":\"\"},\"meta\":[{\"param\":\"wrong\",\"param_type\":\"FLOAT\",\"direction\":\"OUTPUT\"}]}");

        ModelAssetEntity asset = new ModelAssetEntity();
        asset.setId(9L);
        asset.setProfileId(2L);
        asset.setFileType("PY");
        asset.setVersion("v1");
        asset.setIoSchema("{\"inputs\":[{\"name\":\"temperature\",\"type\":\"FLOAT\",\"required\":true}],"
            + "\"outputs\":[{\"name\":\"power\",\"type\":\"FLOAT\"}]}");

        when(associationRuleRepository.findById(1L)).thenReturn(Optional.of(rule));
        when(modelAssetRepository.findById(9L)).thenReturn(Optional.of(asset));

        TaskSubmitRequest request = new TaskSubmitRequest();
        request.setRuleId(1L);
        TaskSubmitRequest.TimeRange range = new TaskSubmitRequest.TimeRange();
        range.setStart(LocalDateTime.of(2026, 3, 22, 10, 0, 0));
        range.setEnd(LocalDateTime.of(2026, 3, 22, 10, 30, 0));
        request.setTimeRange(range);

        BizException ex = assertThrows(BizException.class, () -> taskService.submitTask(request));
        assertTrue(ex.getMessage().contains("不一致"));
        verify(taskRepository, never()).save(any(TaskEntity.class));
        verify(taskScheduler, never()).submit(any(String.class), any(Runnable.class));
    }

    /**
     * 历史规则即使输出 JSON 顺序被打乱，只要名称集合一致，
     * 也应按模型输出顺序生成默认写回路径。
     */
    @Test
    void submitTask_shouldAlignOutputBindingsByNameAndResolveDefaultPaths() throws Exception {
        AssociationRuleEntity rule = new AssociationRuleEntity();
        rule.setId(7L);
        rule.setEnabled(true);
        rule.setModelId(9L);
        rule.setName("qqweqwe");
        rule.setFunctionName("quality_cost_model");
        rule.setMappingJson("{\"function_name\":\"quality_cost_model\",\"mappings\":["
            + "{\"param\":\"temperature\",\"param_type\":\"FLOAT\",\"direction\":\"INPUT\",\"source_path\":\"rt.quality.temperature\"},"
            + "{\"param\":\"pressure\",\"param_type\":\"FLOAT\",\"direction\":\"INPUT\",\"source_path\":\"rt.quality.pressure\"},"
            + "{\"param\":\"flow\",\"param_type\":\"FLOAT\",\"direction\":\"INPUT\",\"source_path\":\"rt.quality.flow\"},"
            + "{\"param\":\"humidity\",\"param_type\":\"FLOAT\",\"direction\":\"INPUT\",\"source_path\":\"rt.quality.humidity\"},"
            + "{\"param\":\"load_rate\",\"param_type\":\"FLOAT\",\"direction\":\"INPUT\",\"source_path\":\"rt.quality.load_rate\"}],"
            + "\"output_target\":{\"paths\":{\"energy_cost\":\"\",\"warning_flag\":\"\",\"quality_score\":\"\"}}}");
        rule.setOutputTarget("{\"paths\":{\"energy_cost\":\"\",\"warning_flag\":\"\",\"quality_score\":\"\"},\"meta\":["
            + "{\"param\":\"energy_cost\",\"param_type\":\"FLOAT\",\"direction\":\"OUTPUT\"},"
            + "{\"param\":\"warning_flag\",\"param_type\":\"INT\",\"direction\":\"OUTPUT\"},"
            + "{\"param\":\"quality_score\",\"param_type\":\"FLOAT\",\"direction\":\"OUTPUT\"}]}");

        ModelAssetEntity asset = new ModelAssetEntity();
        asset.setId(9L);
        asset.setProfileId(2L);
        asset.setFileType("MAT");
        asset.setVersion("v1");
        asset.setIoSchema("{\"inputs\":["
            + "{\"name\":\"temperature\",\"type\":\"FLOAT\",\"required\":true},"
            + "{\"name\":\"pressure\",\"type\":\"FLOAT\",\"required\":true},"
            + "{\"name\":\"flow\",\"type\":\"FLOAT\",\"required\":true},"
            + "{\"name\":\"humidity\",\"type\":\"FLOAT\",\"required\":true},"
            + "{\"name\":\"load_rate\",\"type\":\"FLOAT\",\"required\":true}],"
            + "\"outputs\":["
            + "{\"name\":\"quality_score\",\"type\":\"FLOAT\"},"
            + "{\"name\":\"energy_cost\",\"type\":\"FLOAT\"},"
            + "{\"name\":\"warning_flag\",\"type\":\"INT\"}]}");

        when(associationRuleRepository.findById(7L)).thenReturn(Optional.of(rule));
        when(modelAssetRepository.findById(9L)).thenReturn(Optional.of(asset));
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskSubmitRequest request = new TaskSubmitRequest();
        request.setRuleId(7L);

        String taskId = taskService.submitTask(request);

        ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository).save(captor.capture());
        TaskEntity saved = captor.getValue();
        TaskExecutionSnapshot snapshot = new ObjectMapper().readValue(saved.getExecutionSnapshot(), TaskExecutionSnapshot.class);

        assertEquals("task.result." + taskId, saved.getResultLink());
        assertEquals(3, snapshot.getOutputs().size());
        assertEquals("quality_score", snapshot.getOutputs().get(0).getName());
        assertEquals("task.result." + taskId + ".quality_score", snapshot.getOutputs().get(0).getResolvedPath());
        assertEquals("energy_cost", snapshot.getOutputs().get(1).getName());
        assertEquals("task.result." + taskId + ".energy_cost", snapshot.getOutputs().get(1).getResolvedPath());
        assertEquals("warning_flag", snapshot.getOutputs().get(2).getName());
        assertEquals("task.result." + taskId + ".warning_flag", snapshot.getOutputs().get(2).getResolvedPath());
        verify(taskScheduler).submit(eq(taskId), any(Runnable.class));
    }
}
