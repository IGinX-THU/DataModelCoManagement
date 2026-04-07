package com.xmu.iginx.assoc.modules.taskchain.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.model.util.ModelFileStorageService;
import com.xmu.iginx.assoc.modules.relation.entity.AssociationRuleEntity;
import com.xmu.iginx.assoc.modules.relation.repository.AssociationRuleRepository;
import com.xmu.iginx.assoc.modules.taskchain.dto.TaskChainRunRequest;
import com.xmu.iginx.assoc.modules.task.service.TaskScheduler;
import com.xmu.iginx.assoc.modules.task.service.impl.TaskModelExecutionEngine;
import com.xmu.iginx.assoc.modules.taskchain.dto.TaskChainSaveRequest;
import com.xmu.iginx.assoc.modules.taskchain.entity.TaskChainEntity;
import com.xmu.iginx.assoc.modules.taskchain.entity.TaskChainRunEntity;
import com.xmu.iginx.assoc.modules.taskchain.model.TaskChainDefinition;
import com.xmu.iginx.assoc.modules.taskchain.model.TaskChainInputSource;
import com.xmu.iginx.assoc.modules.taskchain.model.TaskChainNodeDefinition;
import com.xmu.iginx.assoc.modules.taskchain.model.TaskChainRuleDescriptor;
import com.xmu.iginx.assoc.modules.taskchain.repository.TaskChainRepository;
import com.xmu.iginx.assoc.modules.taskchain.repository.TaskChainRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务链服务测试。
 */
@ExtendWith(MockitoExtension.class)
class TaskChainServiceImplTest {

    @Mock
    private TaskChainRepository taskChainRepository;

    @Mock
    private TaskChainRunRepository taskChainRunRepository;

    @Mock
    private AssociationRuleRepository associationRuleRepository;

    @Mock
    private TaskChainRuleResolver taskChainRuleResolver;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private TaskModelExecutionEngine taskModelExecutionEngine;

    @Mock
    private ModelFileStorageService modelFileStorageService;

    private TaskChainServiceImpl taskChainService;

    @BeforeEach
    void setUp() {
        taskChainService = new TaskChainServiceImpl(
            taskChainRepository,
            taskChainRunRepository,
            associationRuleRepository,
            taskChainRuleResolver,
            taskScheduler,
            taskModelExecutionEngine,
            modelFileStorageService,
            new ObjectMapper()
        );
    }

    /**
     * 同一任务链中若混用时序与结构化规则，应直接拒绝创建。
     */
    @Test
    void createChain_shouldRejectWhenRulesMixModes() {
        TaskChainSaveRequest request = new TaskChainSaveRequest();
        request.setChainName("混合模式链");
        request.setNodes(List.of(
            createNode("node_a", 1L, null, null),
            createNode("node_b", 2L, null, null)
        ));

        when(taskChainRuleResolver.resolveDescriptor(1L)).thenReturn(buildDescriptor(1L, "规则A", "TIME_SERIES", "temperature", "ts.factory.temp", "power", "FLOAT"));
        when(taskChainRuleResolver.resolveDescriptor(2L)).thenReturn(buildDescriptor(2L, "规则B", "STRUCTURED", "temperature", "rt.factory.temp", "power", "FLOAT"));

        BizException ex = assertThrows(BizException.class, () -> taskChainService.createChain(request));
        assertTrue(ex.getMessage().contains("节点类型必须一致"));
    }

    /**
     * 若节点之间形成环，应拒绝保存。
     */
    @Test
    void createChain_shouldRejectWhenChainContainsCycle() {
        TaskChainSaveRequest request = new TaskChainSaveRequest();
        request.setChainName("成环链");
        request.setNodes(List.of(
            createNode("node_a", 1L, "node_b", "power"),
            createNode("node_b", 2L, "node_a", "power")
        ));

        when(taskChainRuleResolver.resolveDescriptor(1L)).thenReturn(buildDescriptor(1L, "规则A", "TIME_SERIES", "temperature", "ts.factory.temp", "power", "FLOAT"));
        when(taskChainRuleResolver.resolveDescriptor(2L)).thenReturn(buildDescriptor(2L, "规则B", "TIME_SERIES", "temperature", "ts.factory.temp", "power", "FLOAT"));

        BizException ex = assertThrows(BizException.class, () -> taskChainService.createChain(request));
        assertTrue(ex.getMessage().contains("成环"));
    }

    /**
     * 未显式填写输入来源时，应自动回填规则默认输入路径并持久化。
     */
    @Test
    void createChain_shouldFillDefaultInputPathWhenSourceMissing() throws Exception {
        TaskChainSaveRequest request = new TaskChainSaveRequest();
        request.setChainName("默认路径链");
        request.setNodes(List.of(createNode("node_a", 1L, null, null)));

        when(taskChainRuleResolver.resolveDescriptor(1L)).thenReturn(buildDescriptor(1L, "规则A", "TIME_SERIES", "temperature", "ts.factory.temp", "power", "FLOAT"));
        when(taskChainRepository.save(any(TaskChainEntity.class))).thenAnswer(invocation -> {
            TaskChainEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return entity;
        });

        Long chainId = taskChainService.createChain(request);

        assertEquals(100L, chainId);
        ArgumentCaptor<TaskChainEntity> captor = ArgumentCaptor.forClass(TaskChainEntity.class);
        verify(taskChainRepository).save(captor.capture());
        TaskChainEntity saved = captor.getValue();
        assertEquals("TIME_SERIES", saved.getChainMode());

        TaskChainDefinition definition = new ObjectMapper().readValue(saved.getDefinitionJson(), TaskChainDefinition.class);
        assertEquals("ts.factory.temp", definition.getNodes().get(0).getInputs().get("temperature").getPath());
    }

    /**
     * 任务链结果前缀中的动态段必须以字母开头，避免被 IGinX SQL 解析为数字字面量。
     */
    @Test
    void submitRun_shouldUseSafeResultPrefixSegments() throws Exception {
        TaskChainEntity chain = new TaskChainEntity();
        chain.setId(42L);
        chain.setChainName("安全结果前缀链");
        chain.setChainMode("TIME_SERIES");
        chain.setDefinitionJson(new ObjectMapper().writeValueAsString(buildDefinition("TIME_SERIES")));

        when(taskChainRepository.findById(42L)).thenReturn(Optional.of(chain));
        when(taskChainRuleResolver.resolveDescriptor(1L)).thenReturn(buildDescriptor(1L, "规则A", "TIME_SERIES", "temperature", "ts.factory.temp", "power", "FLOAT"));
        when(taskChainRunRepository.save(any(TaskChainRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskChainRunRequest request = new TaskChainRunRequest();
        TaskChainRunRequest.TimeRange timeRange = new TaskChainRunRequest.TimeRange();
        timeRange.setStart(LocalDateTime.of(2026, 4, 6, 8, 0, 0));
        timeRange.setEnd(LocalDateTime.of(2026, 4, 6, 9, 0, 0));
        request.setTimeRange(timeRange);

        String runId = taskChainService.submitRun(42L, request);

        ArgumentCaptor<TaskChainRunEntity> captor = ArgumentCaptor.forClass(TaskChainRunEntity.class);
        verify(taskChainRunRepository).save(captor.capture());
        TaskChainRunEntity savedRun = captor.getValue();
        assertEquals(runId, savedRun.getId());
        assertTrue(savedRun.getResultPrefix().startsWith("ts.chain.chain_42.run_"));
    }

    private TaskChainSaveRequest.NodeRequest createNode(String nodeId,
                                                        Long ruleId,
                                                        String upstreamNodeId,
                                                        String upstreamOutputName) {
        TaskChainSaveRequest.NodeRequest node = new TaskChainSaveRequest.NodeRequest();
        node.setNodeId(nodeId);
        node.setNodeName(nodeId);
        node.setRuleId(ruleId);
        if (upstreamNodeId != null) {
            TaskChainSaveRequest.InputSourceRequest source = new TaskChainSaveRequest.InputSourceRequest();
            source.setSourceType("UPSTREAM");
            source.setSourceNodeId(upstreamNodeId);
            source.setSourceOutputName(upstreamOutputName);
            node.setInputs(java.util.Map.of("temperature", source));
        }
        return node;
    }

    private TaskChainDefinition buildDefinition(String chainMode) {
        TaskChainInputSource inputSource = new TaskChainInputSource();
        inputSource.setSourceType("PATH");
        inputSource.setPath("ts.factory.temp");

        TaskChainNodeDefinition node = new TaskChainNodeDefinition();
        node.setNodeId("node_a");
        node.setNodeName("node_a");
        node.setRuleId(1L);
        node.setInputs(Map.of("temperature", inputSource));

        TaskChainDefinition definition = new TaskChainDefinition();
        definition.setChainName("安全结果前缀链");
        definition.setChainMode(chainMode);
        definition.setNodes(List.of(node));
        return definition;
    }

    private TaskChainRuleDescriptor buildDescriptor(Long ruleId,
                                                    String ruleName,
                                                    String chainMode,
                                                    String inputName,
                                                    String defaultPath,
                                                    String outputName,
                                                    String outputType) {
        TaskChainRuleDescriptor descriptor = new TaskChainRuleDescriptor();
        descriptor.setRuleId(ruleId);
        descriptor.setRuleName(ruleName);
        descriptor.setEnabled(true);
        descriptor.setModelId(10L + ruleId);
        descriptor.setModelName("模型" + ruleId);
        descriptor.setModelVersion("v1");
        descriptor.setModelType("PY");
        descriptor.setModelFileName("demo.py");
        descriptor.setModelStoragePath("storage/demo.py");
        descriptor.setModelFileSize(128L);
        descriptor.setFunctionName("predict");
        descriptor.setChainMode(chainMode);

        TaskChainRuleDescriptor.ParamDescriptor input = new TaskChainRuleDescriptor.ParamDescriptor();
        input.setName(inputName);
        input.setType("FLOAT");
        input.setDefaultPath(defaultPath);
        input.setPathKind("TIME_SERIES".equals(chainMode) ? "TS" : "RT");

        TaskChainRuleDescriptor.ParamDescriptor output = new TaskChainRuleDescriptor.ParamDescriptor();
        output.setName(outputName);
        output.setType(outputType);
        output.setDefaultPath("");
        output.setPathKind("");

        descriptor.setInputs(List.of(input));
        descriptor.setOutputs(List.of(output));
        return descriptor;
    }
}
