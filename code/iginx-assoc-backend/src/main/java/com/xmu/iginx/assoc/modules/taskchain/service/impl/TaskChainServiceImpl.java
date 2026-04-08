package com.xmu.iginx.assoc.modules.taskchain.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.common.exception.ExceptionMessageUtils;
import com.xmu.iginx.assoc.modules.data.dto.DataColumnsDeleteRequest;
import com.xmu.iginx.assoc.modules.data.service.DataMaintainService;
import com.xmu.iginx.assoc.modules.data.util.DataPrefixRules;
import com.xmu.iginx.assoc.modules.data.util.TimeSeriesPathUtils;
import com.xmu.iginx.assoc.modules.model.util.ModelFileStorageService;
import com.xmu.iginx.assoc.modules.relation.entity.AssociationRuleEntity;
import com.xmu.iginx.assoc.modules.relation.repository.AssociationRuleRepository;
import com.xmu.iginx.assoc.modules.task.enums.TaskStatus;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionBinding;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionOutcome;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionPlan;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionSnapshot;
import com.xmu.iginx.assoc.modules.task.service.TaskScheduler;
import com.xmu.iginx.assoc.modules.task.service.impl.TaskModelExecutionEngine;
import com.xmu.iginx.assoc.modules.taskchain.dto.TaskChainRunRequest;
import com.xmu.iginx.assoc.modules.taskchain.dto.TaskChainSaveRequest;
import com.xmu.iginx.assoc.modules.taskchain.entity.TaskChainEntity;
import com.xmu.iginx.assoc.modules.taskchain.entity.TaskChainRunEntity;
import com.xmu.iginx.assoc.modules.taskchain.model.TaskChainDefinition;
import com.xmu.iginx.assoc.modules.taskchain.model.TaskChainInputSource;
import com.xmu.iginx.assoc.modules.taskchain.model.TaskChainNodeDefinition;
import com.xmu.iginx.assoc.modules.taskchain.model.TaskChainNodeRunSnapshot;
import com.xmu.iginx.assoc.modules.taskchain.model.TaskChainRuleDescriptor;
import com.xmu.iginx.assoc.modules.taskchain.model.TaskChainRunSnapshot;
import com.xmu.iginx.assoc.modules.taskchain.repository.TaskChainRepository;
import com.xmu.iginx.assoc.modules.taskchain.repository.TaskChainRunRepository;
import com.xmu.iginx.assoc.modules.taskchain.service.TaskChainService;
import com.xmu.iginx.assoc.modules.taskchain.vo.TaskChainRuleOptionVO;
import com.xmu.iginx.assoc.modules.taskchain.vo.TaskChainRunVO;
import com.xmu.iginx.assoc.modules.taskchain.vo.TaskChainVO;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 任务链服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskChainServiceImpl implements TaskChainService {

    private static final DateTimeFormatter TASK_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter RUN_NAME_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TaskChainRepository taskChainRepository;
    private final TaskChainRunRepository taskChainRunRepository;
    private final AssociationRuleRepository associationRuleRepository;
    private final TaskChainRuleResolver taskChainRuleResolver;
    private final TaskScheduler taskScheduler;
    private final TaskModelExecutionEngine taskModelExecutionEngine;
    private final ModelFileStorageService modelFileStorageService;
    private final ObjectMapper objectMapper;
    private final DataMaintainService dataMaintainService;
    private final Map<String, String> runAbortReasons = new ConcurrentHashMap<>();

    @Override
    public List<TaskChainVO> listChains() {
        return taskChainRepository.findAll(Sort.by(Sort.Direction.DESC, "updateTime"))
            .stream()
            .map(this::toChainVO)
            .toList();
    }

    @Override
    public TaskChainVO getChain(Long chainId) {
        return toChainVO(findChain(chainId));
    }

    @Override
    @Transactional
    public Long createChain(TaskChainSaveRequest request) {
        ValidatedDefinition validated = validateAndNormalize(request);
        TaskChainEntity entity = new TaskChainEntity();
        entity.setChainName(validated.definition().getChainName());
        entity.setChainMode(validated.definition().getChainMode());
        entity.setDefinitionJson(writeJson(validated.definition()));
        entity.setUpdateTime(LocalDateTime.now());
        return taskChainRepository.save(entity).getId();
    }

    @Override
    @Transactional
    public void updateChain(Long chainId, TaskChainSaveRequest request) {
        TaskChainEntity entity = findChain(chainId);
        ValidatedDefinition validated = validateAndNormalize(request);
        entity.setChainName(validated.definition().getChainName());
        entity.setChainMode(validated.definition().getChainMode());
        entity.setDefinitionJson(writeJson(validated.definition()));
        entity.setUpdateTime(LocalDateTime.now());
        taskChainRepository.save(entity);
    }

    @Override
    @Transactional
    public void deleteChain(Long chainId) {
        TaskChainEntity entity = findChain(chainId);
        boolean hasRunning = taskChainRunRepository.existsByChainIdAndStatusIn(chainId,
            List.of(TaskStatus.PENDING.name(), TaskStatus.RUNNING.name()));
        if (hasRunning) {
            throw BizException.badRequest("任务链存在运行中实例，无法删除");
        }
        taskChainRunRepository.deleteByChainId(chainId);
        taskChainRepository.delete(entity);
    }

    @Override
    public List<TaskChainRuleOptionVO> listCompatibleRules() {
        List<TaskChainRuleOptionVO> result = new ArrayList<>();
        for (AssociationRuleEntity rule : associationRuleRepository.findAll()) {
            try {
                TaskChainRuleDescriptor descriptor = taskChainRuleResolver.resolveDescriptor(rule.getId());
                if (!Boolean.TRUE.equals(descriptor.getEnabled())) {
                    continue;
                }
                result.add(toRuleOptionVO(descriptor));
            } catch (Exception ignored) {
            }
        }
        result.sort((left, right) -> String.valueOf(left.getRuleName()).compareToIgnoreCase(String.valueOf(right.getRuleName())));
        return result;
    }

    @Override
    @Transactional
    public String submitRun(Long chainId, TaskChainRunRequest request) {
        TaskChainEntity chain = findChain(chainId);
        ValidatedDefinition validated = validateAndNormalize(toSaveRequest(readDefinition(chain.getDefinitionJson())));
        validateRunWindow(validated.definition().getChainMode(), request);

        String runId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime createTime = LocalDateTime.now();
        String runName = resolveRunName(request.getRunName(), validated.definition().getChainName(), createTime, runId);
        String resultPrefix = buildResultPrefix(validated.definition().getChainMode(), chainId, runId);
        TaskChainRunSnapshot snapshot = buildRunSnapshot(chain, validated, resultPrefix);

        TaskChainRunEntity run = new TaskChainRunEntity();
        run.setId(runId);
        run.setChainId(chainId);
        run.setRunName(runName);
        run.setStatus(TaskStatus.PENDING.name());
        run.setChainMode(validated.definition().getChainMode());
        run.setRangeStart(request.getTimeRange() == null ? null : request.getTimeRange().getStart());
        run.setRangeEnd(request.getTimeRange() == null ? null : request.getTimeRange().getEnd());
        run.setScheduledStartTime(request.getScheduledStartTime());
        run.setScheduledEndTime(request.getScheduledEndTime());
        run.setResultPrefix(resultPrefix);
        run.setRunSnapshot(writeJson(snapshot));
        run.setExecLog(buildPendingExecLog(validated.definition().getChainMode(),
            request.getScheduledStartTime(), request.getScheduledEndTime()));
        run.setCreateTime(createTime);
        taskChainRunRepository.save(run);

        Runnable runner = () -> executeRun(runId, validated);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    scheduleRun(runId, runner, request.getScheduledStartTime(), request.getScheduledEndTime());
                }
            });
        } else {
            scheduleRun(runId, runner, request.getScheduledStartTime(), request.getScheduledEndTime());
        }
        return runId;
    }

    @Override
    @Transactional
    public void stopRun(String runId) {
        abortRun(runId, "任务链在开始执行前被用户终止", "任务链被用户终止");
    }

    /**
     * 删除失败或已中止的任务链运行记录。
     */
    @Override
    @Transactional
    public void deleteRun(String runId) {
        TaskChainRunEntity run = findRun(runId);
        ensureRunRecordDeletable(run);
        cleanupRunOutputs(run);
        taskScheduler.clear(runId);
        runAbortReasons.remove(runId);
        taskChainRunRepository.delete(run);
    }

    @Override
    public List<TaskChainRunVO> listRuns(Long chainId) {
        List<TaskChainRunEntity> entities = chainId == null
            ? taskChainRunRepository.findAll(Sort.by(Sort.Direction.DESC, "createTime"))
            : taskChainRunRepository.findByChainIdOrderByCreateTimeDesc(chainId);
        return entities.stream().map(this::toRunVO).toList();
    }

    @Override
    public TaskChainRunVO getRun(String runId) {
        return toRunVO(findRun(runId));
    }

    /**
     * 调度任务链运行。
     */
    private void scheduleRun(String runId,
                             Runnable runner,
                             LocalDateTime scheduledStartTime,
                             LocalDateTime scheduledEndTime) {
        try {
            if (scheduledEndTime != null) {
                taskScheduler.scheduleDeadline(runId, scheduledEndTime,
                    () -> abortRun(runId,
                        buildTimeoutPendingAbortMessage(scheduledEndTime),
                        buildTimeoutRunningAbortMessage(scheduledEndTime)));
            }
            if (scheduledStartTime != null && scheduledStartTime.isAfter(LocalDateTime.now())) {
                taskScheduler.schedule(runId, runner, scheduledStartTime,
                    ex -> markRunFailed(runId, ExceptionMessageUtils.buildDetailedMessage("任务链提交失败", ex)));
            } else {
                taskScheduler.submit(runId, runner);
            }
        } catch (BizException ex) {
            taskScheduler.clear(runId);
            markRunFailed(runId, ExceptionMessageUtils.buildDetailedMessage("任务链提交失败", ex));
            throw ex;
        }
    }

    /**
     * 执行任务链。
     */
    private void executeRun(String runId, ValidatedDefinition validated) {
        TaskChainRunEntity run = findRun(runId);
        if (isTerminalStatus(run.getStatus())) {
            taskScheduler.clear(runId);
            runAbortReasons.remove(runId);
            return;
        }
        TaskChainRunSnapshot snapshot = readRunSnapshot(run.getRunSnapshot());
        Map<String, TaskChainNodeDefinition> nodeDefinitions = validated.definition().getNodes().stream()
            .collect(Collectors.toMap(TaskChainNodeDefinition::getNodeId, item -> item, (left, right) -> left, LinkedHashMap::new));
        Map<String, TaskChainNodeRunSnapshot> nodeRuntimeMap = snapshot.getNodes() == null
            ? new LinkedHashMap<>()
            : snapshot.getNodes().stream()
                .collect(Collectors.toMap(TaskChainNodeRunSnapshot::getNodeId, item -> item, (left, right) -> left, LinkedHashMap::new));

        run.setStatus(TaskStatus.RUNNING.name());
        run.setStartTime(LocalDateTime.now());
        run.setExecLog(buildRunningExecLog(snapshot.getChainName(), snapshot.getChainMode(), run.getScheduledEndTime()));
        taskChainRunRepository.save(run);

        TaskChainNodeRunSnapshot currentNode = null;
        try {
            for (String nodeId : validated.topologicalOrder()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("任务链执行被中止");
                }
                currentNode = nodeRuntimeMap.get(nodeId);
                TaskChainNodeDefinition nodeDefinition = nodeDefinitions.get(nodeId);
                TaskChainRuleDescriptor descriptor = validated.nodeDescriptors().get(nodeId);
                if (currentNode == null || nodeDefinition == null || descriptor == null) {
                    throw BizException.internal("任务链节点上下文不完整");
                }

                currentNode.setStatus(TaskStatus.RUNNING.name());
                currentNode.setStartTime(LocalDateTime.now());
                currentNode.setExecLog("节点开始执行");
                persistRunSnapshot(run, snapshot);

                validateUpstreamOutputs(nodeDefinition, nodeRuntimeMap, snapshot.getChainMode());
                String taskId = UUID.randomUUID().toString().replace("-", "");
                TaskExecutionPlan plan = buildNodePlan(taskId, run, snapshot, nodeDefinition, descriptor, nodeRuntimeMap);
                byte[] modelBytes = modelFileStorageService.readAsBytes(descriptor.getModelStoragePath(), descriptor.getModelFileSize());
                TaskExecutionOutcome outcome = taskModelExecutionEngine.execute(plan, modelBytes);

                currentNode.setStatus(TaskStatus.SUCCESS.name());
                currentNode.setEndTime(LocalDateTime.now());
                currentNode.setExecLog(outcome.getExecLog());
                currentNode.setOutputPaths(resolveOutputPaths(plan.getSnapshot()));
                currentNode.setOutputValueCounts(outcome.getOutputValueCounts());
                persistRunSnapshot(run, snapshot);
            }
            run.setStatus(TaskStatus.SUCCESS.name());
            run.setEndTime(LocalDateTime.now());
            run.setExecLog(appendRunLog(run.getExecLog(), "任务链执行成功"));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (currentNode != null && !isTerminalStatus(currentNode.getStatus())) {
                currentNode.setStatus(TaskStatus.ABORTED.name());
                currentNode.setEndTime(LocalDateTime.now());
                currentNode.setExecLog(resolveAbortReason(runId, "任务链执行过程中被终止"));
                persistRunSnapshot(run, snapshot);
            }
            run.setStatus(TaskStatus.ABORTED.name());
            run.setExecLog(resolveAbortReason(runId, "任务链执行过程中被终止"));
        } catch (BizException ex) {
            if (currentNode != null && TaskStatus.RUNNING.name().equals(currentNode.getStatus())) {
                currentNode.setStatus(TaskStatus.FAILED.name());
                currentNode.setEndTime(LocalDateTime.now());
                currentNode.setExecLog(ExceptionMessageUtils.buildDetailedMessage("节点执行失败", ex));
                persistRunSnapshot(run, snapshot);
            }
            run.setStatus(TaskStatus.FAILED.name());
            run.setExecLog(appendRunLog(run.getExecLog(), ExceptionMessageUtils.buildDetailedMessage("任务链执行失败", ex)));
        } catch (Exception ex) {
            if (currentNode != null && TaskStatus.RUNNING.name().equals(currentNode.getStatus())) {
                currentNode.setStatus(TaskStatus.FAILED.name());
                currentNode.setEndTime(LocalDateTime.now());
                currentNode.setExecLog(ExceptionMessageUtils.buildDetailedMessage("节点执行失败", ex));
                persistRunSnapshot(run, snapshot);
            }
            log.error("任务链执行失败，runId={}", runId, ex);
            run.setStatus(TaskStatus.FAILED.name());
            run.setExecLog(appendRunLog(run.getExecLog(), ExceptionMessageUtils.buildDetailedMessage("任务链执行失败", ex)));
        } finally {
            run.setEndTime(run.getEndTime() == null ? LocalDateTime.now() : run.getEndTime());
            run.setRunSnapshot(writeJson(snapshot));
            taskChainRunRepository.save(run);
            taskScheduler.clear(runId);
            runAbortReasons.remove(runId);
        }
    }

    /**
     * 构建节点执行计划。
     */
    private TaskExecutionPlan buildNodePlan(String taskId,
                                            TaskChainRunEntity run,
                                            TaskChainRunSnapshot snapshot,
                                            TaskChainNodeDefinition nodeDefinition,
                                            TaskChainRuleDescriptor descriptor,
                                            Map<String, TaskChainNodeRunSnapshot> nodeRuntimeMap) {
        String nodePrefix = TimeSeriesPathUtils.joinPath(snapshot.getResultPrefix(), nodeDefinition.getNodeId());
        TaskExecutionSnapshot executionSnapshot = new TaskExecutionSnapshot();
        executionSnapshot.setRuleId(descriptor.getRuleId());
        executionSnapshot.setModelId(descriptor.getModelId());
        executionSnapshot.setModelVersion(descriptor.getModelVersion());
        executionSnapshot.setModelType(descriptor.getModelType());
        executionSnapshot.setFunctionName(descriptor.getFunctionName());
        executionSnapshot.setDefaultResultPrefix(nodePrefix);
        executionSnapshot.setRequiresTimeRange("TIME_SERIES".equals(snapshot.getChainMode()));
        executionSnapshot.setRangeStart(run.getRangeStart());
        executionSnapshot.setRangeEnd(run.getRangeEnd());
        executionSnapshot.setInputs(buildNodeInputs(nodeDefinition, descriptor, nodeRuntimeMap));
        executionSnapshot.setOutputs(buildNodeOutputs(nodePrefix, descriptor));

        TaskExecutionPlan plan = new TaskExecutionPlan();
        plan.setTaskId(taskId);
        plan.setRuleId(descriptor.getRuleId());
        plan.setRuleName(descriptor.getRuleName());
        plan.setModelId(descriptor.getModelId());
        plan.setModelVersion(descriptor.getModelVersion());
        plan.setModelType(descriptor.getModelType());
        plan.setModelFileName(descriptor.getModelFileName());
        plan.setModelStoragePath(descriptor.getModelStoragePath());
        plan.setModelFileSize(descriptor.getModelFileSize());
        plan.setSnapshot(executionSnapshot);
        return plan;
    }

    /**
     * 构建节点输入绑定。
     */
    private List<TaskExecutionBinding> buildNodeInputs(TaskChainNodeDefinition nodeDefinition,
                                                       TaskChainRuleDescriptor descriptor,
                                                       Map<String, TaskChainNodeRunSnapshot> nodeRuntimeMap) {
        List<TaskExecutionBinding> result = new ArrayList<>();
        for (TaskChainRuleDescriptor.ParamDescriptor input : safeParams(descriptor.getInputs())) {
            TaskChainInputSource source = nodeDefinition.getInputs() == null ? null : nodeDefinition.getInputs().get(input.getName());
            String path = resolveInputPath(source, nodeRuntimeMap);
            TaskExecutionBinding binding = new TaskExecutionBinding();
            binding.setName(input.getName());
            binding.setType(input.getType());
            binding.setDirection("INPUT");
            binding.setConfiguredPath(path);
            binding.setResolvedPath(path);
            binding.setPathKind(resolveInputPathKind(path));
            result.add(binding);
        }
        return result;
    }

    /**
     * 构建节点输出绑定。
     */
    private List<TaskExecutionBinding> buildNodeOutputs(String nodePrefix, TaskChainRuleDescriptor descriptor) {
        List<TaskExecutionBinding> result = new ArrayList<>();
        for (TaskChainRuleDescriptor.ParamDescriptor output : safeParams(descriptor.getOutputs())) {
            String path = TimeSeriesPathUtils.joinPath(nodePrefix, output.getName());
            TaskExecutionBinding binding = new TaskExecutionBinding();
            binding.setName(output.getName());
            binding.setType(output.getType());
            binding.setDirection("OUTPUT");
            binding.setConfiguredPath(path);
            binding.setResolvedPath(path);
            binding.setPathKind("CHAIN_RESULT");
            result.add(binding);
        }
        return result;
    }

    /**
     * 解析链节点输入路径。
     */
    private String resolveInputPath(TaskChainInputSource source,
                                    Map<String, TaskChainNodeRunSnapshot> nodeRuntimeMap) {
        if (source == null) {
            throw BizException.badRequest("节点输入来源不能为空");
        }
        String sourceType = normalizeSourceType(source.getSourceType());
        if ("UPSTREAM".equals(sourceType)) {
            TaskChainNodeRunSnapshot upstream = nodeRuntimeMap.get(source.getSourceNodeId());
            if (upstream == null || upstream.getOutputPaths() == null) {
                throw BizException.badRequest("未找到上游节点输出: " + source.getSourceNodeId());
            }
            String resolved = upstream.getOutputPaths().get(source.getSourceOutputName());
            if (!StringUtils.hasText(resolved)) {
                throw BizException.badRequest("上游节点输出不存在: " + source.getSourceNodeId() + "." + source.getSourceOutputName());
            }
            return resolved;
        }
        return normalizePath(source.getPath(), "节点输入路径");
    }

    /**
     * 校验上游输出记录数。
     */
    private void validateUpstreamOutputs(TaskChainNodeDefinition nodeDefinition,
                                         Map<String, TaskChainNodeRunSnapshot> nodeRuntimeMap,
                                         String chainMode) {
        List<Integer> outputCounts = new ArrayList<>();
        if (nodeDefinition.getInputs() == null) {
            return;
        }
        for (TaskChainInputSource source : nodeDefinition.getInputs().values()) {
            if (source == null || !"UPSTREAM".equals(normalizeSourceType(source.getSourceType()))) {
                continue;
            }
            TaskChainNodeRunSnapshot upstream = nodeRuntimeMap.get(source.getSourceNodeId());
            if (upstream == null || upstream.getOutputValueCounts() == null) {
                throw BizException.badRequest("未找到上游节点输出统计信息: " + source.getSourceNodeId());
            }
            Integer count = upstream.getOutputValueCounts().get(source.getSourceOutputName());
            if (count == null || count <= 0) {
                throw BizException.badRequest("上游节点输出为空，无法继续执行: "
                    + source.getSourceNodeId() + "." + source.getSourceOutputName());
            }
            outputCounts.add(count);
        }
        if (outputCounts.size() <= 1) {
            return;
        }
        if (new LinkedHashSet<>(outputCounts).size() > 1) {
            throw BizException.badRequest(("TIME_SERIES".equals(chainMode) ? "时序" : "结构化")
                + "链路中多个上游输出的数据数量不一致，无法安全拼接执行");
        }
    }

    /**
     * 构建运行快照。
     */
    private TaskChainRunSnapshot buildRunSnapshot(TaskChainEntity chain,
                                                  ValidatedDefinition validated,
                                                  String resultPrefix) {
        TaskChainRunSnapshot snapshot = new TaskChainRunSnapshot();
        snapshot.setChainId(chain.getId());
        snapshot.setChainName(chain.getChainName());
        snapshot.setChainMode(validated.definition().getChainMode());
        snapshot.setResultPrefix(resultPrefix);
        List<TaskChainNodeRunSnapshot> nodes = new ArrayList<>();
        for (String nodeId : validated.topologicalOrder()) {
            TaskChainNodeDefinition node = validated.nodeMap().get(nodeId);
            TaskChainRuleDescriptor descriptor = validated.nodeDescriptors().get(nodeId);
            TaskChainNodeRunSnapshot item = new TaskChainNodeRunSnapshot();
            item.setNodeId(node.getNodeId());
            item.setNodeName(node.getNodeName());
            item.setRuleId(node.getRuleId());
            item.setRuleName(descriptor.getRuleName());
            item.setFunctionName(descriptor.getFunctionName());
            item.setModelName(descriptor.getModelName());
            item.setModelVersion(descriptor.getModelVersion());
            item.setModelType(descriptor.getModelType());
            item.setStatus(TaskStatus.PENDING.name());
            item.setExecLog("等待执行");
            item.setOutputPaths(Collections.emptyMap());
            item.setOutputValueCounts(Collections.emptyMap());
            nodes.add(item);
        }
        snapshot.setNodes(nodes);
        return snapshot;
    }

    /**
     * 校验并标准化任务链定义。
     */
    private ValidatedDefinition validateAndNormalize(TaskChainSaveRequest request) {
        if (request == null) {
            throw BizException.badRequest("任务链定义不能为空");
        }
        if (request.getNodes() == null || request.getNodes().isEmpty()) {
            throw BizException.badRequest("任务链至少需要一个节点");
        }
        Map<String, TaskChainNodeDefinition> nodeMap = new LinkedHashMap<>();
        Map<String, TaskChainRuleDescriptor> nodeDescriptors = new LinkedHashMap<>();
        String chainMode = "";

        for (TaskChainSaveRequest.NodeRequest nodeRequest : request.getNodes()) {
            if (nodeRequest == null) {
                continue;
            }
            String nodeId = normalizeNodeId(nodeRequest.getNodeId());
            if (nodeMap.containsKey(nodeId)) {
                throw BizException.badRequest("节点ID重复: " + nodeId);
            }
            TaskChainRuleDescriptor descriptor = taskChainRuleResolver.resolveDescriptor(nodeRequest.getRuleId());
            if (!StringUtils.hasText(chainMode)) {
                chainMode = descriptor.getChainMode();
            } else if (!chainMode.equals(descriptor.getChainMode())) {
                throw BizException.badRequest("任务链中的节点类型必须一致，不能混用时序与结构化规则");
            }

            Map<String, TaskChainInputSource> normalizedInputs = normalizeInputs(nodeRequest, descriptor, chainMode);
            TaskChainNodeDefinition nodeDefinition = new TaskChainNodeDefinition();
            nodeDefinition.setNodeId(nodeId);
            nodeDefinition.setNodeName(resolveNodeName(nodeRequest.getNodeName(), descriptor.getRuleName(), nodeId));
            nodeDefinition.setRuleId(nodeRequest.getRuleId());
            nodeDefinition.setInputs(normalizedInputs);
            nodeMap.put(nodeId, nodeDefinition);
            nodeDescriptors.put(nodeId, descriptor);
        }

        TopologyResult topology = validateTopology(nodeMap, nodeDescriptors, chainMode);
        TaskChainDefinition definition = new TaskChainDefinition();
        definition.setChainName(request.getChainName().trim());
        definition.setChainMode(chainMode);
        definition.setNodes(new ArrayList<>(nodeMap.values()));
        return new ValidatedDefinition(definition, nodeMap, nodeDescriptors, topology.topologicalOrder(), topology.edgeCount());
    }

    /**
     * 标准化节点输入定义。
     */
    private Map<String, TaskChainInputSource> normalizeInputs(TaskChainSaveRequest.NodeRequest nodeRequest,
                                                              TaskChainRuleDescriptor descriptor,
                                                              String chainMode) {
        Map<String, TaskChainSaveRequest.InputSourceRequest> rawInputs = nodeRequest.getInputs() == null
            ? Collections.emptyMap()
            : nodeRequest.getInputs();
        Set<String> actualKeys = rawInputs.keySet().stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> expectedKeys = safeParams(descriptor.getInputs()).stream()
            .map(TaskChainRuleDescriptor.ParamDescriptor::getName)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> unknownKeys = new LinkedHashSet<>(actualKeys);
        unknownKeys.removeAll(expectedKeys);
        if (!unknownKeys.isEmpty()) {
            throw BizException.badRequest("节点[" + nodeRequest.getNodeId() + "] 存在未知输入参数: " + unknownKeys);
        }

        Map<String, TaskChainInputSource> result = new LinkedHashMap<>();
        for (TaskChainRuleDescriptor.ParamDescriptor input : safeParams(descriptor.getInputs())) {
            TaskChainSaveRequest.InputSourceRequest rawSource = rawInputs.get(input.getName());
            TaskChainInputSource source = normalizeInputSource(rawSource, input, chainMode);
            result.put(input.getName(), source);
        }
        return result;
    }

    /**
     * 标准化单个输入来源。
     */
    private TaskChainInputSource normalizeInputSource(TaskChainSaveRequest.InputSourceRequest rawSource,
                                                      TaskChainRuleDescriptor.ParamDescriptor input,
                                                      String chainMode) {
        TaskChainInputSource source = new TaskChainInputSource();
        String sourceType = rawSource == null ? "" : rawSource.getSourceType();
        if (!StringUtils.hasText(sourceType)) {
            if (rawSource != null && StringUtils.hasText(rawSource.getSourceNodeId()) && StringUtils.hasText(rawSource.getSourceOutputName())) {
                sourceType = "UPSTREAM";
            } else {
                sourceType = "PATH";
            }
        }
        sourceType = normalizeSourceType(sourceType);
        source.setSourceType(sourceType);
        if ("UPSTREAM".equals(sourceType)) {
            String sourceNodeId = rawSource == null ? "" : normalizeNodeId(rawSource.getSourceNodeId());
            String sourceOutputName = rawSource == null ? "" : safeTrim(rawSource.getSourceOutputName());
            if (!StringUtils.hasText(sourceNodeId) || !StringUtils.hasText(sourceOutputName)) {
                throw BizException.badRequest("输入参数[" + input.getName() + "] 缺少上游节点输出定义");
            }
            source.setSourceNodeId(sourceNodeId);
            source.setSourceOutputName(sourceOutputName);
            source.setPath("");
            return source;
        }

        String path = rawSource == null || !StringUtils.hasText(rawSource.getPath())
            ? input.getDefaultPath()
            : rawSource.getPath();
        path = normalizePath(path, "输入参数[" + input.getName() + "] 路径");
        ensurePathMatchesChainMode(path, chainMode, "输入参数[" + input.getName() + "]");
        source.setPath(path);
        source.setSourceNodeId("");
        source.setSourceOutputName("");
        return source;
    }

    /**
     * 校验 DAG 与上游输出绑定。
     */
    private TopologyResult validateTopology(Map<String, TaskChainNodeDefinition> nodeMap,
                                            Map<String, TaskChainRuleDescriptor> nodeDescriptors,
                                            String chainMode) {
        Map<String, Set<String>> edges = new LinkedHashMap<>();
        Map<String, Integer> indegree = new LinkedHashMap<>();
        int edgeCount = 0;
        for (String nodeId : nodeMap.keySet()) {
            edges.put(nodeId, new LinkedHashSet<>());
            indegree.put(nodeId, 0);
        }
        for (TaskChainNodeDefinition node : nodeMap.values()) {
            TaskChainRuleDescriptor descriptor = nodeDescriptors.get(node.getNodeId());
            Map<String, TaskChainRuleDescriptor.ParamDescriptor> inputIndex = safeParams(descriptor.getInputs()).stream()
                .collect(Collectors.toMap(TaskChainRuleDescriptor.ParamDescriptor::getName,
                    item -> item, (left, right) -> left, LinkedHashMap::new));
            for (Map.Entry<String, TaskChainInputSource> entry : safeInputs(node.getInputs()).entrySet()) {
                TaskChainInputSource source = entry.getValue();
                if (source == null || !"UPSTREAM".equals(normalizeSourceType(source.getSourceType()))) {
                    continue;
                }
                TaskChainNodeDefinition upstreamNode = nodeMap.get(source.getSourceNodeId());
                if (upstreamNode == null) {
                    throw BizException.badRequest("节点[" + node.getNodeId() + "] 依赖的上游节点不存在: " + source.getSourceNodeId());
                }
                if (node.getNodeId().equals(source.getSourceNodeId())) {
                    throw BizException.badRequest("节点[" + node.getNodeId() + "] 不能依赖自身输出");
                }
                TaskChainRuleDescriptor upstreamDescriptor = nodeDescriptors.get(source.getSourceNodeId());
                TaskChainRuleDescriptor.ParamDescriptor inputParam = inputIndex.get(entry.getKey());
                TaskChainRuleDescriptor.ParamDescriptor outputParam = findOutputParam(upstreamDescriptor, source.getSourceOutputName());
                ensureCompatibleTypes(node.getNodeName(), inputParam, upstreamNode.getNodeName(), outputParam);
                if (!chainMode.equals(upstreamDescriptor.getChainMode())) {
                    throw BizException.badRequest("任务链中的节点类型必须一致");
                }
                if (edges.get(source.getSourceNodeId()).add(node.getNodeId())) {
                    indegree.put(node.getNodeId(), indegree.get(node.getNodeId()) + 1);
                }
                edgeCount++;
            }
        }

        ArrayDeque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }
        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            order.add(current);
            for (String next : edges.getOrDefault(current, Set.of())) {
                int updated = indegree.get(next) - 1;
                indegree.put(next, updated);
                if (updated == 0) {
                    queue.add(next);
                }
            }
        }
        if (order.size() != nodeMap.size()) {
            throw BizException.badRequest("任务链存在成环依赖，无法执行");
        }
        return new TopologyResult(order, edgeCount);
    }

    /**
     * 持久化运行快照。
     */
    private void persistRunSnapshot(TaskChainRunEntity run, TaskChainRunSnapshot snapshot) {
        run.setRunSnapshot(writeJson(snapshot));
        taskChainRunRepository.save(run);
    }

    /**
     * 构建任务链视图对象。
     */
    private TaskChainVO toChainVO(TaskChainEntity entity) {
        TaskChainDefinition definition = readDefinition(entity.getDefinitionJson());
        TaskChainVO vo = new TaskChainVO();
        vo.setId(entity.getId());
        vo.setChainName(entity.getChainName());
        vo.setChainMode(entity.getChainMode());
        vo.setUpdateTime(entity.getUpdateTime());
        List<TaskChainNodeDefinition> nodes = definition.getNodes() == null ? List.of() : definition.getNodes();
        vo.setNodeCount(nodes.size());
        vo.setEdgeCount(countEdges(nodes));
        vo.setNodes(nodes.stream().map(this::toNodeVO).toList());
        return vo;
    }

    /**
     * 构建节点视图对象。
     */
    private TaskChainVO.NodeVO toNodeVO(TaskChainNodeDefinition node) {
        TaskChainVO.NodeVO vo = new TaskChainVO.NodeVO();
        vo.setNodeId(node.getNodeId());
        vo.setNodeName(node.getNodeName());
        vo.setRuleId(node.getRuleId());
        vo.setInputSources(convertInputSources(node.getInputs()));
        try {
            TaskChainRuleDescriptor descriptor = taskChainRuleResolver.resolveDescriptor(node.getRuleId());
            vo.setRuleName(descriptor.getRuleName());
            vo.setFunctionName(descriptor.getFunctionName());
            vo.setModelName(descriptor.getModelName());
            vo.setModelVersion(descriptor.getModelVersion());
            vo.setModelType(descriptor.getModelType());
            vo.setAvailableInputs(convertParams(descriptor.getInputs()));
            vo.setAvailableOutputs(convertParams(descriptor.getOutputs()));
        } catch (Exception ex) {
            vo.setRuleName("规则不可用");
            vo.setFunctionName("-");
            vo.setModelName("-");
            vo.setModelVersion("-");
            vo.setModelType("-");
            vo.setAvailableInputs(List.of());
            vo.setAvailableOutputs(List.of());
            vo.setValidationMessage(ex.getMessage());
        }
        return vo;
    }

    /**
     * 构建规则可选项视图。
     */
    private TaskChainRuleOptionVO toRuleOptionVO(TaskChainRuleDescriptor descriptor) {
        TaskChainRuleOptionVO vo = new TaskChainRuleOptionVO();
        vo.setRuleId(descriptor.getRuleId());
        vo.setRuleName(descriptor.getRuleName());
        vo.setModelName(descriptor.getModelName());
        vo.setModelVersion(descriptor.getModelVersion());
        vo.setModelType(descriptor.getModelType());
        vo.setFunctionName(descriptor.getFunctionName());
        vo.setChainMode(descriptor.getChainMode());
        vo.setInputs(convertRuleOptionParams(descriptor.getInputs()));
        vo.setOutputs(convertRuleOptionParams(descriptor.getOutputs()));
        return vo;
    }

    /**
     * 构建任务链运行视图对象。
     */
    private TaskChainRunVO toRunVO(TaskChainRunEntity entity) {
        TaskChainRunSnapshot snapshot = readRunSnapshot(entity.getRunSnapshot());
        TaskChainRunVO vo = new TaskChainRunVO();
        vo.setId(entity.getId());
        vo.setChainId(entity.getChainId());
        vo.setChainName(snapshot.getChainName());
        vo.setRunName(resolveDisplayRunName(entity, snapshot.getChainName()));
        vo.setStatus(entity.getStatus());
        vo.setChainMode(entity.getChainMode());
        vo.setRangeStart(entity.getRangeStart());
        vo.setRangeEnd(entity.getRangeEnd());
        vo.setScheduledStartTime(entity.getScheduledStartTime());
        vo.setScheduledEndTime(entity.getScheduledEndTime());
        vo.setStartTime(entity.getStartTime());
        vo.setEndTime(entity.getEndTime());
        vo.setResultPrefix(entity.getResultPrefix());
        vo.setExecLog(entity.getExecLog());
        vo.setCreateTime(entity.getCreateTime());
        vo.setNodes(snapshot.getNodes() == null ? List.of() : snapshot.getNodes().stream().map(this::toNodeRunVO).toList());
        return vo;
    }

    /**
     * 构建节点运行视图对象。
     */
    private TaskChainRunVO.NodeRunVO toNodeRunVO(TaskChainNodeRunSnapshot snapshot) {
        TaskChainRunVO.NodeRunVO vo = new TaskChainRunVO.NodeRunVO();
        vo.setNodeId(snapshot.getNodeId());
        vo.setNodeName(snapshot.getNodeName());
        vo.setRuleId(snapshot.getRuleId());
        vo.setRuleName(snapshot.getRuleName());
        vo.setFunctionName(snapshot.getFunctionName());
        vo.setModelName(snapshot.getModelName());
        vo.setModelVersion(snapshot.getModelVersion());
        vo.setModelType(snapshot.getModelType());
        vo.setStatus(snapshot.getStatus());
        vo.setStartTime(snapshot.getStartTime());
        vo.setEndTime(snapshot.getEndTime());
        vo.setExecLog(snapshot.getExecLog());
        vo.setOutputPaths(snapshot.getOutputPaths());
        vo.setOutputValueCounts(snapshot.getOutputValueCounts());
        return vo;
    }

    /**
     * 规则参数转任务链节点参数视图。
     */
    private List<TaskChainVO.ParamVO> convertParams(List<TaskChainRuleDescriptor.ParamDescriptor> params) {
        List<TaskChainVO.ParamVO> result = new ArrayList<>();
        for (TaskChainRuleDescriptor.ParamDescriptor param : safeParams(params)) {
            TaskChainVO.ParamVO item = new TaskChainVO.ParamVO();
            item.setName(param.getName());
            item.setType(param.getType());
            item.setDefaultPath(param.getDefaultPath());
            result.add(item);
        }
        return result;
    }

    /**
     * 规则参数转可选项参数视图。
     */
    private List<TaskChainRuleOptionVO.ParamVO> convertRuleOptionParams(List<TaskChainRuleDescriptor.ParamDescriptor> params) {
        List<TaskChainRuleOptionVO.ParamVO> result = new ArrayList<>();
        for (TaskChainRuleDescriptor.ParamDescriptor param : safeParams(params)) {
            TaskChainRuleOptionVO.ParamVO item = new TaskChainRuleOptionVO.ParamVO();
            item.setName(param.getName());
            item.setType(param.getType());
            item.setDefaultPath(param.getDefaultPath());
            result.add(item);
        }
        return result;
    }

    /**
     * 输入来源转前端视图。
     */
    private Map<String, TaskChainVO.InputSourceVO> convertInputSources(Map<String, TaskChainInputSource> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, TaskChainVO.InputSourceVO> result = new LinkedHashMap<>();
        for (Map.Entry<String, TaskChainInputSource> entry : inputs.entrySet()) {
            TaskChainInputSource source = entry.getValue();
            TaskChainVO.InputSourceVO item = new TaskChainVO.InputSourceVO();
            item.setSourceType(source == null ? "" : source.getSourceType());
            item.setPath(source == null ? "" : source.getPath());
            item.setSourceNodeId(source == null ? "" : source.getSourceNodeId());
            item.setSourceOutputName(source == null ? "" : source.getSourceOutputName());
            result.put(entry.getKey(), item);
        }
        return result;
    }

    /**
     * 从执行快照提取输出路径。
     */
    private Map<String, String> resolveOutputPaths(TaskExecutionSnapshot snapshot) {
        if (snapshot == null || snapshot.getOutputs() == null || snapshot.getOutputs().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (TaskExecutionBinding output : snapshot.getOutputs()) {
            if (output == null || !StringUtils.hasText(output.getName()) || !StringUtils.hasText(output.getResolvedPath())) {
                continue;
            }
            result.put(output.getName(), output.getResolvedPath());
        }
        return result;
    }

    /**
     * 统计边数量。
     */
    private int countEdges(List<TaskChainNodeDefinition> nodes) {
        int count = 0;
        for (TaskChainNodeDefinition node : nodes) {
            count += (int) safeInputs(node.getInputs()).values().stream()
                .filter(item -> item != null && "UPSTREAM".equals(normalizeSourceType(item.getSourceType())))
                .count();
        }
        return count;
    }

    /**
     * 校验运行时间窗口。
     */
    private void validateRunWindow(String chainMode, TaskChainRunRequest request) {
        TaskChainRunRequest.TimeRange timeRange = request.getTimeRange();
        if ("TIME_SERIES".equals(chainMode)) {
            if (timeRange == null || timeRange.getStart() == null || timeRange.getEnd() == null) {
                throw BizException.badRequest("时序任务链必须选择时间区间");
            }
            if (!timeRange.getEnd().isAfter(timeRange.getStart())) {
                throw BizException.badRequest("时间范围不合法：结束时间必须晚于开始时间");
            }
        } else if (timeRange != null
            && timeRange.getStart() != null
            && timeRange.getEnd() != null
            && !timeRange.getEnd().isAfter(timeRange.getStart())) {
            throw BizException.badRequest("时间范围不合法：结束时间必须晚于开始时间");
        }

        LocalDateTime scheduledEndTime = request.getScheduledEndTime();
        if (scheduledEndTime == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledStartTime = request.getScheduledStartTime();
        LocalDateTime effectiveStartTime = scheduledStartTime != null && scheduledStartTime.isAfter(now)
            ? scheduledStartTime
            : now;
        if (!scheduledEndTime.isAfter(effectiveStartTime)) {
            if (scheduledStartTime != null && scheduledStartTime.isAfter(now)) {
                throw BizException.badRequest("任务链终止时间必须晚于计划开始时间");
            }
            throw BizException.badRequest("任务链终止时间必须晚于当前时间");
        }
    }

    /**
     * 统一终止任务链运行。
     */
    private void abortRun(String runId, String pendingMessage, String runningMessage) {
        TaskChainRunEntity run;
        try {
            run = findRun(runId);
        } catch (BizException ex) {
            runAbortReasons.remove(runId);
            taskScheduler.clear(runId);
            return;
        }
        if (isTerminalStatus(run.getStatus())) {
            runAbortReasons.remove(runId);
            taskScheduler.clear(runId);
            return;
        }
        boolean started = run.getStartTime() != null || TaskStatus.RUNNING.name().equals(run.getStatus());
        String message = started ? runningMessage : pendingMessage;
        runAbortReasons.put(runId, message);
        taskScheduler.cancel(runId);
        run.setStatus(TaskStatus.ABORTED.name());
        run.setExecLog(message);
        if (!started) {
            run.setEndTime(LocalDateTime.now());
        }
        taskChainRunRepository.save(run);
        if (!started) {
            runAbortReasons.remove(runId);
            taskScheduler.clear(runId);
        }
    }

    /**
     * 标记运行失败。
     */
    private void markRunFailed(String runId, String message) {
        try {
            TaskChainRunEntity run = findRun(runId);
            run.setStatus(TaskStatus.FAILED.name());
            run.setEndTime(LocalDateTime.now());
            run.setExecLog(message);
            taskChainRunRepository.save(run);
        } catch (Exception ignored) {
        } finally {
            taskScheduler.clear(runId);
            runAbortReasons.remove(runId);
        }
    }

    /**
     * 删除任务链运行记录前，先清理该次运行已经写出的输出路径。
     * <p>
     * 任务链输出前缀按 runId 唯一化，因此优先按 resultPrefix 级联删除；
     * 若历史数据缺少 resultPrefix，则回退到节点快照中的具体输出路径逐条删除。
     * </p>
     *
     * @param run 运行实体
     */
    private void cleanupRunOutputs(TaskChainRunEntity run) {
        Map<String, Boolean> deleteTargets = collectRunDeleteTargets(run);
        for (Map.Entry<String, Boolean> entry : deleteTargets.entrySet()) {
            DataColumnsDeleteRequest request = new DataColumnsDeleteRequest();
            request.setPath(entry.getKey());
            request.setIncludeChildren(entry.getValue());
            dataMaintainService.deleteColumns(request);
        }
    }

    private TaskChainEntity findChain(Long chainId) {
        return taskChainRepository.findById(chainId)
            .orElseThrow(() -> BizException.badRequest("任务链不存在，id=" + chainId));
    }

    private TaskChainRunEntity findRun(String runId) {
        return taskChainRunRepository.findById(runId)
            .orElseThrow(() -> BizException.badRequest("任务链运行记录不存在，id=" + runId));
    }

    /**
     * 仅允许删除失败或已中止的任务链运行记录，避免误删正常历史。
     */
    private void ensureRunRecordDeletable(TaskChainRunEntity run) {
        String status = run == null ? "" : run.getStatus();
        if (TaskStatus.FAILED.name().equals(status) || TaskStatus.ABORTED.name().equals(status)) {
            return;
        }
        throw BizException.badRequest("仅允许删除失败或已中止的任务链运行记录");
    }

    /**
     * 汇总任务链运行删除时需要清理的输出路径。
     *
     * @param run 运行实体
     * @return 路径 -> 是否级联删除子路径
     */
    private Map<String, Boolean> collectRunDeleteTargets(TaskChainRunEntity run) {
        LinkedHashMap<String, Boolean> targets = new LinkedHashMap<>();
        if (run == null) {
            return targets;
        }
        if (StringUtils.hasText(run.getResultPrefix())) {
            addDeleteTarget(targets, run.getResultPrefix(), true);
            return targets;
        }
        TaskChainRunSnapshot snapshot = readRunSnapshotForCleanup(run.getRunSnapshot());
        if (snapshot == null || snapshot.getNodes() == null || snapshot.getNodes().isEmpty()) {
            return targets;
        }
        for (TaskChainNodeRunSnapshot node : snapshot.getNodes()) {
            if (node == null || node.getOutputPaths() == null || node.getOutputPaths().isEmpty()) {
                continue;
            }
            for (String path : node.getOutputPaths().values()) {
                addDeleteTarget(targets, path, false);
            }
        }
        return targets;
    }

    /**
     * 将路径加入任务链清理集合，避免父子路径重复删除。
     *
     * @param targets 路径集合
     * @param path 路径
     * @param includeChildren 是否级联删除
     */
    private void addDeleteTarget(Map<String, Boolean> targets, String path, boolean includeChildren) {
        String normalized = TimeSeriesPathUtils.normalizePath(path);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        for (Map.Entry<String, Boolean> entry : new ArrayList<>(targets.entrySet())) {
            String existingPath = entry.getKey();
            boolean existingIncludeChildren = Boolean.TRUE.equals(entry.getValue());
            if (existingPath.equals(normalized)) {
                targets.put(existingPath, existingIncludeChildren || includeChildren);
                return;
            }
            if (existingIncludeChildren && TimeSeriesPathUtils.startsWithPath(normalized, existingPath)) {
                return;
            }
            if (includeChildren && TimeSeriesPathUtils.startsWithPath(existingPath, normalized)) {
                targets.remove(existingPath);
            }
        }
        targets.put(normalized, includeChildren);
    }

    /**
     * 解析任务链清理场景使用的运行快照。
     *
     * @param json 快照 JSON
     * @return 运行快照
     */
    private TaskChainRunSnapshot readRunSnapshotForCleanup(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            TaskChainRunSnapshot snapshot = objectMapper.readValue(json, TaskChainRunSnapshot.class);
            return snapshot == null ? null : snapshot;
        } catch (Exception ex) {
            throw BizException.badRequest(
                ExceptionMessageUtils.buildDetailedMessage("任务链运行快照解析失败，无法安全清理输出数据", ex),
                ex
            );
        }
    }

    private TaskChainDefinition readDefinition(String json) {
        if (!StringUtils.hasText(json)) {
            return new TaskChainDefinition();
        }
        try {
            TaskChainDefinition definition = objectMapper.readValue(json, TaskChainDefinition.class);
            return definition == null ? new TaskChainDefinition() : definition;
        } catch (Exception ex) {
            throw BizException.badRequest(ExceptionMessageUtils.buildDetailedMessage("任务链定义解析失败", ex), ex);
        }
    }

    private TaskChainRunSnapshot readRunSnapshot(String json) {
        if (!StringUtils.hasText(json)) {
            return new TaskChainRunSnapshot();
        }
        try {
            TaskChainRunSnapshot snapshot = objectMapper.readValue(json, TaskChainRunSnapshot.class);
            return snapshot == null ? new TaskChainRunSnapshot() : snapshot;
        } catch (Exception ex) {
            throw BizException.badRequest(ExceptionMessageUtils.buildDetailedMessage("任务链运行快照解析失败", ex), ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw BizException.internal(ExceptionMessageUtils.buildDetailedMessage("任务链快照序列化失败", ex), ex);
        }
    }

    private TaskChainSaveRequest toSaveRequest(TaskChainDefinition definition) {
        TaskChainSaveRequest request = new TaskChainSaveRequest();
        request.setChainName(definition.getChainName());
        List<TaskChainSaveRequest.NodeRequest> nodes = new ArrayList<>();
        for (TaskChainNodeDefinition node : definition.getNodes() == null ? List.<TaskChainNodeDefinition>of() : definition.getNodes()) {
            TaskChainSaveRequest.NodeRequest item = new TaskChainSaveRequest.NodeRequest();
            item.setNodeId(node.getNodeId());
            item.setNodeName(node.getNodeName());
            item.setRuleId(node.getRuleId());
            Map<String, TaskChainSaveRequest.InputSourceRequest> inputs = new LinkedHashMap<>();
            for (Map.Entry<String, TaskChainInputSource> entry : safeInputs(node.getInputs()).entrySet()) {
                TaskChainInputSource source = entry.getValue();
                TaskChainSaveRequest.InputSourceRequest sourceRequest = new TaskChainSaveRequest.InputSourceRequest();
                sourceRequest.setSourceType(source == null ? "" : source.getSourceType());
                sourceRequest.setPath(source == null ? "" : source.getPath());
                sourceRequest.setSourceNodeId(source == null ? "" : source.getSourceNodeId());
                sourceRequest.setSourceOutputName(source == null ? "" : source.getSourceOutputName());
                inputs.put(entry.getKey(), sourceRequest);
            }
            item.setInputs(inputs);
            nodes.add(item);
        }
        request.setNodes(nodes);
        return request;
    }

    private String resolveRunName(String rawRunName, String chainName, LocalDateTime createTime, String runId) {
        if (StringUtils.hasText(rawRunName)) {
            return rawRunName.trim();
        }
        String baseName = StringUtils.hasText(chainName) ? chainName.trim() : "任务链运行";
        if (createTime != null) {
            return baseName + "_" + createTime.format(RUN_NAME_TIME_FORMATTER);
        }
        return baseName + "_" + runId.substring(0, Math.min(8, runId.length()));
    }

    private String resolveDisplayRunName(TaskChainRunEntity entity, String chainName) {
        if (entity != null && StringUtils.hasText(entity.getRunName())) {
            return entity.getRunName().trim();
        }
        return resolveRunName("", chainName, entity == null ? null : entity.getCreateTime(), entity == null ? "" : entity.getId());
    }

    private String buildResultPrefix(String chainMode, Long chainId, String runId) {
        String prefix = "TIME_SERIES".equals(chainMode) ? DataPrefixRules.TS_PREFIX : DataPrefixRules.RT_PREFIX;
        return prefix + ".chain.chain_" + chainId + ".run_" + runId;
    }

    private String buildPendingExecLog(String chainMode,
                                       LocalDateTime scheduledStartTime,
                                       LocalDateTime scheduledEndTime) {
        StringBuilder builder = new StringBuilder("任务链已提交");
        builder.append("，执行模式: ").append("TIME_SERIES".equals(chainMode) ? "时序" : "结构化");
        if (scheduledStartTime != null && scheduledStartTime.isAfter(LocalDateTime.now())) {
            builder.append("，将于 ").append(formatTaskTime(scheduledStartTime)).append(" 开始执行");
        } else {
            builder.append("，等待执行");
        }
        if (scheduledEndTime != null) {
            builder.append("，最晚终止时间: ").append(formatTaskTime(scheduledEndTime));
        }
        return builder.toString();
    }

    private String buildRunningExecLog(String chainName, String chainMode, LocalDateTime scheduledEndTime) {
        StringBuilder builder = new StringBuilder("任务链开始执行：")
            .append(chainName)
            .append("，模式: ")
            .append("TIME_SERIES".equals(chainMode) ? "时序" : "结构化");
        if (scheduledEndTime != null) {
            builder.append("，最晚终止时间: ").append(formatTaskTime(scheduledEndTime));
        }
        return builder.toString();
    }

    private String appendRunLog(String currentLog, String line) {
        String current = currentLog == null ? "" : currentLog.trim();
        if (!StringUtils.hasText(current)) {
            return line;
        }
        return current + "\n" + line;
    }

    private String buildTimeoutPendingAbortMessage(LocalDateTime scheduledEndTime) {
        return "任务链在计划开始前已到达终止时间(" + formatTaskTime(scheduledEndTime) + ")，系统已取消执行";
    }

    private String buildTimeoutRunningAbortMessage(LocalDateTime scheduledEndTime) {
        return "任务链到达执行终止时间(" + formatTaskTime(scheduledEndTime) + ")，系统已强制终止";
    }

    private String resolveAbortReason(String runId, String defaultMessage) {
        return runAbortReasons.getOrDefault(runId, defaultMessage);
    }

    private String formatTaskTime(LocalDateTime time) {
        return time == null ? "-" : time.format(TASK_TIME_FORMATTER);
    }

    private boolean isTerminalStatus(String status) {
        return TaskStatus.SUCCESS.name().equals(status)
            || TaskStatus.FAILED.name().equals(status)
            || TaskStatus.ABORTED.name().equals(status);
    }

    private TaskChainRuleDescriptor.ParamDescriptor findOutputParam(TaskChainRuleDescriptor descriptor, String outputName) {
        return safeParams(descriptor.getOutputs()).stream()
            .filter(item -> item != null && safeTrim(item.getName()).equals(safeTrim(outputName)))
            .findFirst()
            .orElseThrow(() -> BizException.badRequest("未找到上游输出参数: " + outputName));
    }

    private void ensureCompatibleTypes(String targetNodeName,
                                       TaskChainRuleDescriptor.ParamDescriptor inputParam,
                                       String sourceNodeName,
                                       TaskChainRuleDescriptor.ParamDescriptor outputParam) {
        if (inputParam == null || outputParam == null) {
            throw BizException.badRequest("任务链节点参数定义不完整");
        }
        if (safeTrim(inputParam.getType()).equals(safeTrim(outputParam.getType()))) {
            return;
        }
        throw BizException.badRequest("节点[" + targetNodeName + "] 输入参数[" + inputParam.getName()
            + "] 与上游节点[" + sourceNodeName + "] 输出参数[" + outputParam.getName()
            + "] 类型不一致");
    }

    private void ensurePathMatchesChainMode(String path, String chainMode, String fieldName) {
        if ("TIME_SERIES".equals(chainMode) && !DataPrefixRules.startsWithPrefix(path, DataPrefixRules.TS_PREFIX)) {
            throw BizException.badRequest(fieldName + " 必须绑定 ts 路径");
        }
        if ("STRUCTURED".equals(chainMode) && !DataPrefixRules.startsWithPrefix(path, DataPrefixRules.RT_PREFIX)) {
            throw BizException.badRequest(fieldName + " 必须绑定 rt 路径");
        }
    }

    private String normalizeSourceType(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return "PATH";
        }
        String type = rawType.trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "UPSTREAM", "NODE_OUTPUT" -> "UPSTREAM";
            default -> "PATH";
        };
    }

    private String normalizePath(String path, String fieldName) {
        String normalized = TimeSeriesPathUtils.normalizePath(path);
        if (!StringUtils.hasText(normalized)) {
            throw BizException.badRequest(fieldName + "不能为空");
        }
        if (normalized.contains("*")
            || normalized.contains(";")
            || normalized.contains(" ")
            || normalized.contains("\t")
            || normalized.contains("\n")
            || normalized.contains("\r")) {
            throw BizException.badRequest(fieldName + "包含非法字符");
        }
        return normalized;
    }

    private String normalizeNodeId(String rawNodeId) {
        String nodeId = safeTrim(rawNodeId);
        if (!StringUtils.hasText(nodeId)) {
            throw BizException.badRequest("节点ID不能为空");
        }
        if (!nodeId.matches("^[A-Za-z][A-Za-z0-9_]{0,31}$")) {
            throw BizException.badRequest("节点ID格式不合法: " + nodeId);
        }
        return nodeId;
    }

    private String resolveNodeName(String rawName, String ruleName, String nodeId) {
        if (StringUtils.hasText(rawName)) {
            return rawName.trim();
        }
        if (StringUtils.hasText(ruleName)) {
            return ruleName.trim();
        }
        return "节点_" + nodeId;
    }

    private String resolveInputPathKind(String path) {
        if (DataPrefixRules.startsWithPrefix(path, DataPrefixRules.TS_PREFIX)) {
            return "TS";
        }
        if (DataPrefixRules.startsWithPrefix(path, DataPrefixRules.RT_PREFIX)) {
            return "RT";
        }
        throw BizException.badRequest("输入路径必须以 ts 或 rt 开头: " + path);
    }

    private List<TaskChainRuleDescriptor.ParamDescriptor> safeParams(List<TaskChainRuleDescriptor.ParamDescriptor> params) {
        return params == null ? List.of() : params;
    }

    private Map<String, TaskChainInputSource> safeInputs(Map<String, TaskChainInputSource> inputs) {
        return inputs == null ? Collections.emptyMap() : inputs;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private record ValidatedDefinition(TaskChainDefinition definition,
                                       Map<String, TaskChainNodeDefinition> nodeMap,
                                       Map<String, TaskChainRuleDescriptor> nodeDescriptors,
                                       List<String> topologicalOrder,
                                       int edgeCount) {
    }

    private record TopologyResult(List<String> topologicalOrder, int edgeCount) {
    }
}
