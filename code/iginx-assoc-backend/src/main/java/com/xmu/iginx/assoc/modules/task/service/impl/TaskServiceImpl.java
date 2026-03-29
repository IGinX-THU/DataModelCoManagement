package com.xmu.iginx.assoc.modules.task.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.data.util.DataPrefixRules;
import com.xmu.iginx.assoc.modules.data.util.TimeSeriesPathUtils;
import com.xmu.iginx.assoc.modules.model.dto.ModelIoSchema;
import com.xmu.iginx.assoc.modules.model.dto.ModelSchemaParam;
import com.xmu.iginx.assoc.modules.model.entity.MetaModelProfileEntity;
import com.xmu.iginx.assoc.modules.model.entity.ModelAssetEntity;
import com.xmu.iginx.assoc.modules.model.repository.MetaModelProfileRepository;
import com.xmu.iginx.assoc.modules.model.repository.ModelAssetRepository;
import com.xmu.iginx.assoc.modules.model.util.ModelFileStorageService;
import com.xmu.iginx.assoc.modules.model.util.ModelFunctionSchemaParser;
import com.xmu.iginx.assoc.modules.relation.entity.AssociationRuleEntity;
import com.xmu.iginx.assoc.modules.relation.repository.AssociationRuleRepository;
import com.xmu.iginx.assoc.modules.task.dto.TaskSubmitRequest;
import com.xmu.iginx.assoc.modules.task.entity.TaskEntity;
import com.xmu.iginx.assoc.modules.task.enums.TaskStatus;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionBinding;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionPlan;
import com.xmu.iginx.assoc.modules.task.model.TaskExecutionSnapshot;
import com.xmu.iginx.assoc.modules.task.repository.TaskRepository;
import com.xmu.iginx.assoc.modules.task.service.TaskScheduler;
import com.xmu.iginx.assoc.modules.task.service.TaskService;
import com.xmu.iginx.assoc.modules.task.vo.TaskVO;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 任务服务实现。
 * <p>
 * 核心职责：
 * 1. 提交任务前做严格运行前校验；
 * 2. 生成任务执行快照，固化真实执行上下文；
 * 3. 异步调度模型执行并更新任务状态；
 * 4. 提供任务查询与终止能力。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final AssociationRuleRepository associationRuleRepository;
    private final ModelAssetRepository modelAssetRepository;
    private final MetaModelProfileRepository profileRepository;
    private final TaskScheduler taskScheduler;
    private final ObjectMapper objectMapper;
    private final TaskModelExecutionEngine taskModelExecutionEngine;
    private final ModelFileStorageService modelFileStorageService;
    private final ModelFunctionSchemaParser functionSchemaParser;

    /**
     * 提交任务。
     */
    @Override
    @Transactional
    public String submitTask(TaskSubmitRequest request) {
        AssociationRuleEntity rule = associationRuleRepository.findById(request.getRuleId())
            .orElseThrow(() -> BizException.badRequest("关联规则不存在"));
        if (!Boolean.TRUE.equals(rule.getEnabled())) {
            throw BizException.badRequest("规则未启用，无法执行");
        }
        ModelAssetEntity asset = modelAssetRepository.findById(rule.getModelId())
            .orElseThrow(() -> BizException.badRequest("模型版本不存在"));

        String taskId = UUID.randomUUID().toString().replace("-", "");
        TaskExecutionPlan plan = buildExecutionPlan(taskId, rule, asset, request);

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setRuleId(rule.getId());
        task.setStatus(TaskStatus.PENDING.name());
        task.setRangeStart(plan.getSnapshot().getRangeStart());
        task.setRangeEnd(plan.getSnapshot().getRangeEnd());
        task.setCreateTime(LocalDateTime.now());
        task.setResultLink(resolveResultLink(plan.getSnapshot()));
        task.setExecutionSnapshot(writeJson(plan.getSnapshot()));
        task.setExecLog("任务已提交，等待执行");
        taskRepository.save(task);

        Runnable runner = () -> executeTask(taskId, plan);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitAsyncTask(taskId, runner);
                }
            });
        } else {
            submitAsyncTask(taskId, runner);
        }
        return taskId;
    }

    /**
     * 终止任务。
     */
    @Override
    @Transactional
    public void stopTask(String taskId) {
        TaskEntity task = findTask(taskId);
        if (TaskStatus.SUCCESS.name().equals(task.getStatus())
            || TaskStatus.FAILED.name().equals(task.getStatus())
            || TaskStatus.ABORTED.name().equals(task.getStatus())) {
            return;
        }
        taskScheduler.cancel(taskId);
        task.setStatus(TaskStatus.ABORTED.name());
        task.setEndTime(LocalDateTime.now());
        task.setExecLog("任务被用户终止");
        taskRepository.save(task);
    }

    /**
     * 查询任务列表。
     */
    @Override
    public List<TaskVO> listTasks(Long ruleId) {
        List<TaskEntity> entities = ruleId == null
            ? taskRepository.findAll(Sort.by(Sort.Direction.DESC, "createTime"))
            : taskRepository.findByRuleIdOrderByCreateTimeDesc(ruleId);
        return entities.stream().map(this::toVO).toList();
    }

    /**
     * 查询任务详情。
     */
    @Override
    public TaskVO getTask(String taskId) {
        return toVO(findTask(taskId));
    }

    /**
     * 异步提交任务。
     */
    private void submitAsyncTask(String taskId, Runnable runner) {
        try {
            taskScheduler.submit(taskId, runner);
        } catch (BizException ex) {
            markTaskFailed(taskId, "任务提交失败: " + ex.getMessage());
            throw ex;
        }
    }

    /**
     * 执行任务。
     */
    private void executeTask(String taskId, TaskExecutionPlan plan) {
        TaskEntity task = findTask(taskId);
        task.setStatus(TaskStatus.RUNNING.name());
        task.setStartTime(LocalDateTime.now());
        task.setExecLog("任务开始执行，模型函数: " + plan.getSnapshot().getFunctionName());
        taskRepository.save(task);

        try {
            byte[] modelBytes = modelFileStorageService.readAsBytes(plan.getModelStoragePath(), plan.getModelFileSize());
            var outcome = taskModelExecutionEngine.execute(plan, modelBytes);
            task.setStatus(TaskStatus.SUCCESS.name());
            task.setExecLog(outcome.getExecLog());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            task.setStatus(TaskStatus.ABORTED.name());
            task.setExecLog("任务执行过程中被终止");
        } catch (BizException ex) {
            if (Thread.currentThread().isInterrupted()) {
                task.setStatus(TaskStatus.ABORTED.name());
                task.setExecLog("任务执行过程中被终止");
            } else {
                task.setStatus(TaskStatus.FAILED.name());
                task.setExecLog("任务执行失败: " + ex.getMessage());
            }
        } catch (Exception ex) {
            if (Thread.currentThread().isInterrupted()) {
                task.setStatus(TaskStatus.ABORTED.name());
                task.setExecLog("任务执行过程中被终止");
            } else {
                log.error("任务执行失败，taskId={}", taskId, ex);
                task.setStatus(TaskStatus.FAILED.name());
                task.setExecLog("任务执行失败: " + ex.getMessage());
            }
        } finally {
            task.setEndTime(LocalDateTime.now());
            taskRepository.save(task);
            taskScheduler.clear(taskId);
        }
    }

    /**
     * 构建执行计划并做提交前严格校验。
     */
    private TaskExecutionPlan buildExecutionPlan(String taskId,
                                                 AssociationRuleEntity rule,
                                                 ModelAssetEntity asset,
                                                 TaskSubmitRequest request) {
        String functionName = resolveFunctionName(rule);
        ModelIoSchema schema = resolveRuntimeSchema(asset, functionName);
        List<ModelSchemaParam> inputParams = normalizeSchemaParams(schema.getInputs(), "输入");
        List<ModelSchemaParam> outputParams = normalizeSchemaParams(schema.getOutputs(), "输出");
        if (inputParams.isEmpty()) {
            throw BizException.badRequest("模型未定义输入参数，无法执行任务");
        }
        if (outputParams.isEmpty()) {
            throw BizException.badRequest("模型未定义输出参数，无法执行任务");
        }

        ParsedRuleBindings parsedRuleBindings = parseRuleBindings(rule);
        ensureExactBindings("输入", inputParams, parsedRuleBindings.inputs(), "INPUT");
        ensureExactBindings("输出", outputParams, parsedRuleBindings.outputs(), "OUTPUT");

        boolean requiresTimeRange = parsedRuleBindings.inputs().stream()
            .anyMatch(item -> "TS".equals(item.pathKind()));
        TaskSubmitRequest.TimeRange timeRange = request.getTimeRange();
        if (requiresTimeRange) {
            if (timeRange == null || timeRange.getStart() == null || timeRange.getEnd() == null) {
                throw BizException.badRequest("任务输入包含 ts 路径，必须选择时间区间");
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

        String defaultPrefix = "task.result." + taskId;
        TaskExecutionSnapshot snapshot = new TaskExecutionSnapshot();
        snapshot.setRuleId(rule.getId());
        snapshot.setModelId(asset.getId());
        snapshot.setModelVersion(asset.getVersion());
        snapshot.setModelType(normalizeModelType(asset.getFileType()));
        snapshot.setFunctionName(functionName);
        snapshot.setDefaultResultPrefix(defaultPrefix);
        snapshot.setRequiresTimeRange(requiresTimeRange);
        snapshot.setRangeStart(timeRange == null ? null : timeRange.getStart());
        snapshot.setRangeEnd(timeRange == null ? null : timeRange.getEnd());
        snapshot.setInputs(buildOrderedInputBindings(inputParams, parsedRuleBindings.inputs()));
        snapshot.setOutputs(buildOrderedOutputBindings(outputParams, parsedRuleBindings.outputs(), defaultPrefix));

        TaskExecutionPlan plan = new TaskExecutionPlan();
        plan.setTaskId(taskId);
        plan.setRuleId(rule.getId());
        plan.setRuleName(rule.getName());
        plan.setModelId(asset.getId());
        plan.setModelVersion(asset.getVersion());
        plan.setModelType(normalizeModelType(asset.getFileType()));
        plan.setModelFileName(asset.getFileName());
        plan.setModelStoragePath(asset.getStoragePath());
        plan.setModelFileSize(asset.getFileSize());
        plan.setSnapshot(snapshot);
        return plan;
    }

    /**
     * 解析规则中的输入输出绑定。
     */
    private ParsedRuleBindings parseRuleBindings(AssociationRuleEntity rule) {
        List<RuleBinding> inputs = parseInputBindings(rule.getMappingJson());
        List<RuleBinding> outputs = parseOutputBindings(rule.getOutputTarget());
        if (inputs.isEmpty()) {
            throw BizException.badRequest("关联规则缺少输入绑定");
        }
        if (outputs.isEmpty()) {
            throw BizException.badRequest("关联规则缺少输出绑定");
        }
        return new ParsedRuleBindings(inputs, outputs);
    }

    /**
     * 解析输入绑定。
     */
    private List<RuleBinding> parseInputBindings(String mappingJson) {
        if (!StringUtils.hasText(mappingJson)) {
            return List.of();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(mappingJson, new TypeReference<>() {
            });
            Object mappingsObj = root.get("mappings");
            if (!(mappingsObj instanceof List<?> list)) {
                return List.of();
            }
            List<RuleBinding> result = new ArrayList<>();
            for (Object itemObj : list) {
                if (!(itemObj instanceof Map<?, ?> item)) {
                    continue;
                }
                String name = safeTrim(item.get("param"));
                String type = normalizeType(safeTrim(item.get("param_type")));
                String direction = normalizeDirection(safeTrim(item.get("direction")), "INPUT");
                String path = normalizePath(safeTrim(item.get("source_path")), "输入路径");
                String pathKind = resolveInputPathKind(path);
                result.add(new RuleBinding(name, type, direction, path, pathKind));
            }
            return result;
        } catch (Exception ex) {
            throw BizException.badRequest("关联规则输入绑定解析失败");
        }
    }

    /**
     * 解析输出绑定。
     * <p>
     * 兼容两类历史数据：
     * 1. 新规则：paths 与 meta 都存在，此时优先按 meta 顺序解析；
     * 2. 旧规则：可能只有 paths，此时退回按 paths 中的名称解析。
     * </p>
     */
    private List<RuleBinding> parseOutputBindings(String outputTargetJson) {
        if (!StringUtils.hasText(outputTargetJson)) {
            return List.of();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(outputTargetJson, new TypeReference<>() {
            });
            Map<String, String> pathMap = new LinkedHashMap<>();
            Object pathsObj = root.get("paths");
            if (pathsObj instanceof Map<?, ?> paths) {
                for (Map.Entry<?, ?> entry : paths.entrySet()) {
                    if (entry.getKey() == null) {
                        continue;
                    }
                    pathMap.put(entry.getKey().toString(), safeTrim(entry.getValue()));
                }
            }
            List<RuleBinding> result = new ArrayList<>();
            Set<String> appendedNames = new LinkedHashSet<>();
            Object metaObj = root.get("meta");
            if (metaObj instanceof List<?> list) {
                for (Object itemObj : list) {
                    if (!(itemObj instanceof Map<?, ?> item)) {
                        continue;
                    }
                    String name = safeTrim(item.get("param"));
                    if (!StringUtils.hasText(name) || !appendedNames.add(name)) {
                        continue;
                    }
                    result.add(new RuleBinding(
                        name,
                        normalizeType(safeTrim(item.get("param_type"))),
                        normalizeDirection(safeTrim(item.get("direction")), "OUTPUT"),
                        normalizeOutputConfiguredPath(pathMap.getOrDefault(name, "")),
                        ""
                    ));
                }
            }
            for (Map.Entry<String, String> entry : pathMap.entrySet()) {
                String name = safeTrim(entry.getKey());
                if (!StringUtils.hasText(name) || !appendedNames.add(name)) {
                    continue;
                }
                result.add(new RuleBinding(
                    name,
                    "",
                    "OUTPUT",
                    normalizeOutputConfiguredPath(entry.getValue()),
                    ""
                ));
            }
            return result;
        } catch (Exception ex) {
            throw BizException.badRequest("关联规则输出绑定解析失败");
        }
    }

    /**
     * 构建有序输入绑定快照。
     */
    private List<TaskExecutionBinding> buildOrderedInputBindings(List<ModelSchemaParam> schemaParams, List<RuleBinding> bindings) {
        Map<String, RuleBinding> bindingIndex = bindings.stream()
            .collect(Collectors.toMap(RuleBinding::name, item -> item, (left, right) -> left, LinkedHashMap::new));
        List<TaskExecutionBinding> result = new ArrayList<>();
        for (ModelSchemaParam param : schemaParams) {
            RuleBinding binding = bindingIndex.get(param.getName());
            TaskExecutionBinding item = new TaskExecutionBinding();
            item.setName(param.getName());
            item.setType(normalizeType(param.getType()));
            item.setDirection("INPUT");
            item.setConfiguredPath(binding.path());
            item.setResolvedPath(binding.path());
            item.setPathKind(binding.pathKind());
            result.add(item);
        }
        return result;
    }

    /**
     * 构建有序输出绑定快照。
     */
    private List<TaskExecutionBinding> buildOrderedOutputBindings(List<ModelSchemaParam> schemaParams,
                                                                  List<RuleBinding> bindings,
                                                                  String defaultPrefix) {
        Map<String, RuleBinding> bindingIndex = bindings.stream()
            .collect(Collectors.toMap(RuleBinding::name, item -> item, (left, right) -> left, LinkedHashMap::new));
        List<TaskExecutionBinding> result = new ArrayList<>();
        for (ModelSchemaParam param : schemaParams) {
            RuleBinding binding = bindingIndex.get(param.getName());
            String configuredPath = binding == null ? "" : binding.path();
            String resolvedPath = StringUtils.hasText(configuredPath)
                ? configuredPath
                : TimeSeriesPathUtils.joinPath(defaultPrefix, param.getName());
            TaskExecutionBinding item = new TaskExecutionBinding();
            item.setName(param.getName());
            item.setType(normalizeType(param.getType()));
            item.setDirection("OUTPUT");
            item.setConfiguredPath(configuredPath);
            item.setResolvedPath(resolvedPath);
            item.setPathKind(StringUtils.hasText(configuredPath) ? "CUSTOM" : "TASK_RESULT");
            result.add(item);
        }
        return result;
    }

    /**
     * 校验规则绑定与运行时模型结构严格一致。
     * <p>
     * 这里按参数名做精确对齐，而不是依赖 JSON 对象键顺序，
     * 避免历史规则在序列化/反序列化后仅因输出顺序变化而误判失败。
     * </p>
     */
    private void ensureExactBindings(String direction,
                                     List<ModelSchemaParam> schemaParams,
                                     List<RuleBinding> bindings,
                                     String expectedDirection) {
        if (schemaParams.size() != bindings.size()) {
            throw BizException.badRequest(direction + "参数数量与模型结构不一致");
        }
        Map<String, RuleBinding> bindingIndex = indexBindingsByName(direction, bindings);
        for (ModelSchemaParam schemaParam : schemaParams) {
            String schemaName = schemaParam.getName();
            RuleBinding binding = bindingIndex.get(schemaName);
            if (binding == null) {
                throw BizException.badRequest(direction + "参数顺序或名称与模型结构不一致: " + schemaName);
            }
            if (!expectedDirection.equals(binding.direction())) {
                throw BizException.badRequest(direction + "参数方向不正确: " + schemaName);
            }
            if ("INPUT".equals(expectedDirection)
                && !("TS".equals(binding.pathKind()) || "RT".equals(binding.pathKind()))) {
                throw BizException.badRequest("输入参数[" + schemaName + "] 路径必须以 ts 或 rt 开头");
            }
            String schemaType = normalizeType(schemaParam.getType());
            if (StringUtils.hasText(binding.type()) && !schemaType.equals(binding.type())) {
                throw BizException.badRequest(direction + "参数[" + schemaName + "] 类型与模型结构不一致");
            }
        }
    }

    /**
     * 按参数名构建绑定索引，并校验重复参数。
     */
    private Map<String, RuleBinding> indexBindingsByName(String direction, List<RuleBinding> bindings) {
        Map<String, RuleBinding> result = new LinkedHashMap<>();
        for (RuleBinding binding : bindings) {
            if (binding == null || !StringUtils.hasText(binding.name())) {
                continue;
            }
            String name = binding.name().trim();
            if (result.containsKey(name)) {
                throw BizException.badRequest(direction + "参数重复: " + name);
            }
            result.put(name, binding);
        }
        return result;
    }

    /**
     * 解析运行时函数名。
     */
    private String resolveFunctionName(AssociationRuleEntity rule) {
        if (StringUtils.hasText(rule.getFunctionName())) {
            return rule.getFunctionName().trim();
        }
        if (!StringUtils.hasText(rule.getMappingJson())) {
            throw BizException.badRequest("关联规则缺少函数定义");
        }
        try {
            Map<String, Object> root = objectMapper.readValue(rule.getMappingJson(), new TypeReference<>() {
            });
            String functionName = safeTrim(root.get("function_name"));
            if (!StringUtils.hasText(functionName)) {
                throw BizException.badRequest("关联规则缺少函数定义");
            }
            return functionName;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.badRequest("关联规则函数定义解析失败");
        }
    }

    /**
     * 解析运行时模型结构。
     * <p>
     * 优先走“模型文件 + 指定函数”解析；若缺少文件存储信息，则回退到资产/档案级 io_schema。
     * </p>
     */
    private ModelIoSchema resolveRuntimeSchema(ModelAssetEntity asset, String functionName) {
        String modelType = normalizeModelType(asset.getFileType());
        if (supportsFunctionBinding(modelType)
            && StringUtils.hasText(asset.getStoragePath())
            && asset.getFileSize() != null
            && asset.getFileSize() > 0) {
            byte[] fileBytes = modelFileStorageService.readAsBytes(asset.getStoragePath(), asset.getFileSize());
            try {
                return functionSchemaParser.parseByFunction(fileBytes, modelType, functionName).schema();
            } catch (IllegalArgumentException ex) {
                throw BizException.badRequest("模型函数不存在或解析失败: " + ex.getMessage());
            } catch (Exception ex) {
                throw BizException.badRequest("模型函数结构解析失败");
            }
        }
        String ioSchemaJson = asset.getIoSchema();
        if (!StringUtils.hasText(ioSchemaJson)) {
            MetaModelProfileEntity profile = profileRepository.findById(asset.getProfileId()).orElse(null);
            ioSchemaJson = profile == null ? null : profile.getIoSchema();
        }
        if (!StringUtils.hasText(ioSchemaJson)) {
            throw BizException.badRequest("模型未定义输入输出结构");
        }
        try {
            return objectMapper.readValue(ioSchemaJson, ModelIoSchema.class);
        } catch (Exception ex) {
            throw BizException.badRequest("模型输入输出结构解析失败");
        }
    }

    /**
     * 规范化模型参数列表。
     */
    private List<ModelSchemaParam> normalizeSchemaParams(List<ModelSchemaParam> params, String direction) {
        if (params == null || params.isEmpty()) {
            return List.of();
        }
        List<ModelSchemaParam> result = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        for (ModelSchemaParam param : params) {
            if (param == null || !StringUtils.hasText(param.getName())) {
                continue;
            }
            String name = param.getName().trim();
            if (!names.add(name)) {
                throw BizException.badRequest("模型" + direction + "参数名重复: " + name);
            }
            ModelSchemaParam copy = new ModelSchemaParam();
            copy.setName(name);
            copy.setType(normalizeType(param.getType()));
            copy.setRequired(param.getRequired());
            copy.setUnit(param.getUnit());
            copy.setDescription(param.getDescription());
            result.add(copy);
        }
        return result;
    }

    /**
     * 任务结果路径摘要。
     */
    private String resolveResultLink(TaskExecutionSnapshot snapshot) {
        List<TaskExecutionBinding> outputs = snapshot.getOutputs() == null ? List.of() : snapshot.getOutputs();
        boolean hasDefault = outputs.stream().anyMatch(item -> "TASK_RESULT".equalsIgnoreCase(item.getPathKind()));
        if (hasDefault) {
            return snapshot.getDefaultResultPrefix();
        }
        List<String> resolvedPaths = outputs.stream()
            .map(TaskExecutionBinding::getResolvedPath)
            .filter(StringUtils::hasText)
            .toList();
        if (resolvedPaths.isEmpty()) {
            return snapshot.getDefaultResultPrefix();
        }
        String commonParent = resolveCommonParent(resolvedPaths);
        if (StringUtils.hasText(commonParent)) {
            return commonParent;
        }
        return resolvedPaths.get(0);
    }

    /**
     * 解析多个路径的公共父前缀。
     */
    private String resolveCommonParent(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return "";
        }
        List<String> first = splitPath(paths.get(0));
        if (first.size() < 2) {
            return "";
        }
        int prefixLength = first.size() - 1;
        for (int i = 1; i < paths.size(); i++) {
            List<String> current = splitPath(paths.get(i));
            prefixLength = Math.min(prefixLength, current.size() - 1);
            for (int j = 0; j < prefixLength; j++) {
                if (!first.get(j).equals(current.get(j))) {
                    prefixLength = j;
                    break;
                }
            }
        }
        if (prefixLength <= 0) {
            return "";
        }
        return String.join(".", first.subList(0, prefixLength));
    }

    /**
     * 分割路径。
     */
    private List<String> splitPath(String path) {
        String normalized = normalizePath(path, "路径");
        return Arrays.stream(normalized.split("\\."))
            .filter(StringUtils::hasText)
            .toList();
    }

    /**
     * 安全写 JSON。
     */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw BizException.internal("任务快照序列化失败");
        }
    }

    /**
     * 标记任务失败。
     */
    private void markTaskFailed(String taskId, String message) {
        try {
            TaskEntity task = findTask(taskId);
            task.setStatus(TaskStatus.FAILED.name());
            task.setEndTime(LocalDateTime.now());
            task.setExecLog(message);
            taskRepository.save(task);
        } catch (Exception ignored) {
        }
    }

    /**
     * 查询任务实体。
     */
    private TaskEntity findTask(String taskId) {
        return taskRepository.findById(taskId)
            .orElseThrow(() -> BizException.badRequest("任务不存在，id=" + taskId));
    }

    /**
     * 转换为 VO。
     */
    private TaskVO toVO(TaskEntity entity) {
        TaskVO vo = new TaskVO();
        vo.setId(entity.getId());
        vo.setRuleId(entity.getRuleId());
        vo.setStatus(entity.getStatus());
        vo.setRangeStart(entity.getRangeStart());
        vo.setRangeEnd(entity.getRangeEnd());
        vo.setStartTime(entity.getStartTime());
        vo.setEndTime(entity.getEndTime());
        vo.setResultLink(entity.getResultLink());
        vo.setOutputPaths(resolveOutputPaths(entity.getExecutionSnapshot()));
        vo.setAnalysisMode(resolveAnalysisMode(entity.getExecutionSnapshot(), entity));
        vo.setExecLog(entity.getExecLog());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /**
     * 从任务执行快照中提取每个输出参数的真实写回路径。
     */
    private Map<String, String> resolveOutputPaths(String executionSnapshotJson) {
        if (!StringUtils.hasText(executionSnapshotJson)) {
            return Collections.emptyMap();
        }
        try {
            TaskExecutionSnapshot snapshot = objectMapper.readValue(executionSnapshotJson, TaskExecutionSnapshot.class);
            if (snapshot == null || snapshot.getOutputs() == null || snapshot.getOutputs().isEmpty()) {
                return Collections.emptyMap();
            }
            Map<String, String> result = new LinkedHashMap<>();
            for (TaskExecutionBinding item : snapshot.getOutputs()) {
                if (item == null || !StringUtils.hasText(item.getName()) || !StringUtils.hasText(item.getResolvedPath())) {
                    continue;
                }
                result.put(item.getName().trim(), item.getResolvedPath().trim());
            }
            return result;
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    /**
     * 解析任务分析展示模式。
     * <p>
     * 规则：
     * 1. 只要输入中存在 ts.* 路径，就按时序任务展示；
     * 2. 否则若存在 rt.* 路径，则按结构化任务展示；
     * 3. 对旧任务快照缺失的情况，回退到是否带时间区间来判断。
     * </p>
     */
    private String resolveAnalysisMode(String executionSnapshotJson, TaskEntity entity) {
        if (StringUtils.hasText(executionSnapshotJson)) {
            try {
                TaskExecutionSnapshot snapshot = objectMapper.readValue(executionSnapshotJson, TaskExecutionSnapshot.class);
                if (snapshot != null && snapshot.getInputs() != null) {
                    boolean hasTs = snapshot.getInputs().stream()
                        .filter(java.util.Objects::nonNull)
                        .anyMatch(item -> "TS".equalsIgnoreCase(item.getPathKind()));
                    if (hasTs) {
                        return "TIME_SERIES";
                    }
                    boolean hasRt = snapshot.getInputs().stream()
                        .filter(java.util.Objects::nonNull)
                        .anyMatch(item -> "RT".equalsIgnoreCase(item.getPathKind()));
                    if (hasRt) {
                        return "STRUCTURED";
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return entity != null && (entity.getRangeStart() != null || entity.getRangeEnd() != null)
            ? "TIME_SERIES"
            : "STRUCTURED";
    }

    /**
     * 输入路径类型。
     */
    private String resolveInputPathKind(String path) {
        if (DataPrefixRules.startsWithPrefix(path, DataPrefixRules.TS_PREFIX)) {
            return "TS";
        }
        if (DataPrefixRules.startsWithPrefix(path, DataPrefixRules.RT_PREFIX)) {
            return "RT";
        }
        throw BizException.badRequest("输入路径必须以 ts 或 rt 开头: " + path);
    }

    /**
     * 输出路径允许为空；非空时做规范化校验。
     */
    private String normalizeOutputConfiguredPath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        return normalizePath(path, "输出路径");
    }

    /**
     * 规范化路径。
     */
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

    /**
     * 类型归一化。
     */
    private String normalizeType(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return "";
        }
        String type = rawType.trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "DOUBLE", "FLOAT", "REAL", "DECIMAL", "SINGLE" -> "FLOAT";
            case "INT", "INTEGER", "LONG", "SHORT", "INT64", "INT32", "INT16", "INT8", "UINT8", "UINT16", "UINT32" -> "INT";
            case "BOOL", "BOOLEAN", "LOGICAL" -> "BOOLEAN";
            case "ARRAY" -> "ARRAY";
            case "OBJECT", "MAP", "DICT", "JSON" -> "OBJECT";
            default -> "STRING";
        };
    }

    /**
     * 方向归一化。
     */
    private String normalizeDirection(String rawDirection, String defaultDirection) {
        if (!StringUtils.hasText(rawDirection)) {
            return defaultDirection;
        }
        String direction = rawDirection.trim().toUpperCase(Locale.ROOT);
        return switch (direction) {
            case "INPUT" -> "INPUT";
            case "OUTPUT" -> "OUTPUT";
            default -> defaultDirection;
        };
    }

    /**
     * 规范化模型类型。
     */
    private String normalizeModelType(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return "UNKNOWN";
        }
        String type = rawType.trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "PYTHON", "PY" -> "PY";
            case "MATLAB", "MAT" -> "MAT";
            case "C++", "CPP" -> "CPP";
            default -> type;
        };
    }

    /**
     * 是否支持函数级解析。
     */
    private boolean supportsFunctionBinding(String modelType) {
        return "PY".equals(modelType) || "MAT".equals(modelType) || "CPP".equals(modelType);
    }

    /**
     * 安全字符串。
     */
    private String safeTrim(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    /**
     * 规则绑定。
     */
    private record RuleBinding(String name, String type, String direction, String path, String pathKind) {
    }

    /**
     * 已解析规则绑定集合。
     */
    private record ParsedRuleBindings(List<RuleBinding> inputs, List<RuleBinding> outputs) {
    }
}
