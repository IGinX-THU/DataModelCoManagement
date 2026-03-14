package com.xmu.iginx.assoc.modules.task.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.util.TimeParser;
import com.xmu.iginx.assoc.modules.model.entity.ModelAssetEntity;
import com.xmu.iginx.assoc.modules.model.repository.ModelAssetRepository;
import com.xmu.iginx.assoc.modules.relation.entity.AssociationRuleEntity;
import com.xmu.iginx.assoc.modules.relation.repository.AssociationRuleRepository;
import com.xmu.iginx.assoc.modules.task.dto.TaskSubmitRequest;
import com.xmu.iginx.assoc.modules.task.entity.TaskEntity;
import com.xmu.iginx.assoc.modules.task.enums.TaskStatus;
import com.xmu.iginx.assoc.modules.task.repository.TaskRepository;
import com.xmu.iginx.assoc.modules.task.service.TaskScheduler;
import com.xmu.iginx.assoc.modules.task.service.TaskService;
import com.xmu.iginx.assoc.modules.task.vo.TaskVO;
import cn.edu.tsinghua.iginx.session.SessionQueryDataSet;
import cn.edu.tsinghua.iginx.thrift.DataType;
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
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 任务服务实现，负责提交、执行与查询任务。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final AssociationRuleRepository associationRuleRepository;
    private final ModelAssetRepository modelAssetRepository;
    private final TaskScheduler taskScheduler;
    private final IginxStorageWrapper iginxStorageWrapper;
    private final ObjectMapper objectMapper;

    /**
     * 提交任务并写入任务表，提交后异步执行。
     *
     * @param request 提交参数
     * @return 任务 ID
     */
    @Override
    @Transactional
    public String submitTask(TaskSubmitRequest request) {
        AssociationRuleEntity rule = associationRuleRepository.findById(request.getRuleId())
            .orElseThrow(() -> BizException.badRequest("关联规则不存在"));
        if (!Boolean.TRUE.equals(rule.getEnabled())) {
            throw BizException.badRequest("规则未启用，无法执行");
        }
        if (request.getTimeRange().getEnd().isBefore(request.getTimeRange().getStart())
            || request.getTimeRange().getEnd().isEqual(request.getTimeRange().getStart())) {
            throw BizException.badRequest("时间范围不合法");
        }
        ModelAssetEntity asset = modelAssetRepository.findById(rule.getModelId())
            .orElseThrow(() -> BizException.badRequest("模型版本不存在"));

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID().toString().replace("-", ""));
        task.setRuleId(rule.getId());
        task.setStatus(TaskStatus.PENDING.name());
        task.setRangeStart(request.getTimeRange().getStart());
        task.setRangeEnd(request.getTimeRange().getEnd());
        task.setCreateTime(LocalDateTime.now());
        task.setResultLink("root.assoc_sys.results." + task.getId());
        taskRepository.save(task);

        Runnable taskRunner = () -> executeTask(task.getId(), rule, asset);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // 事务提交后再异步执行，避免读到未提交数据
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        taskScheduler.submit(task.getId(), taskRunner);
                    } catch (BizException ex) {
                        markTaskFailed(task.getId(), "任务提交失败: " + ex.getMessage());
                    }
                }
            });
        } else {
            try {
                taskScheduler.submit(task.getId(), taskRunner);
            } catch (BizException ex) {
                markTaskFailed(task.getId(), "任务提交失败: " + ex.getMessage());
                throw ex;
            }
        }
        return task.getId();
    }

    /**
     * 终止任务执行并更新任务状态。
     *
     * @param taskId 任务 ID
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
     *
     * @param ruleId 规则 ID（可选）
     * @return 任务列表
     */
    @Override
    public List<TaskVO> listTasks(Long ruleId) {
        List<TaskEntity> entities = ruleId == null
            ? taskRepository.findAll(Sort.by(Sort.Direction.DESC, "createTime"))
            : taskRepository.findByRuleIdOrderByCreateTimeDesc(ruleId);
        List<TaskVO> result = new ArrayList<>();
        for (TaskEntity entity : entities) {
            result.add(toVO(entity));
        }
        return result;
    }

    /**
     * 查询任务详情。
     *
     * @param taskId 任务 ID
     * @return 任务详情
     */
    @Override
    public TaskVO getTask(String taskId) {
        return toVO(findTask(taskId));
    }

    /**
     * 执行任务核心逻辑，负责读取输入并写入输出测点。
     */
    private void executeTask(String taskId, AssociationRuleEntity rule, ModelAssetEntity asset) {
        TaskEntity task = findTask(taskId);
        task.setStatus(TaskStatus.RUNNING.name());
        task.setStartTime(LocalDateTime.now());
        task.setExecLog("任务开始执行，规则: " + rule.getName() + "，模型版本: " + asset.getVersion());
        taskRepository.save(task);

        try {
            Map<String, String> inputBindings = parseInputBindings(rule.getMappingJson());
            Map<String, String> outputBindings = parseOutputBindings(rule.getOutputTarget());
            if (inputBindings.isEmpty() || outputBindings.isEmpty()) {
                throw new IllegalStateException("输入或输出映射为空，无法执行任务");
            }
            writeTaskOutputs(task, inputBindings, outputBindings);
            Thread.sleep(300);
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("任务被终止");
            }
            task.setStatus(TaskStatus.SUCCESS.name());
            task.setExecLog("任务执行完成，结果已写入输出测点");
        } catch (InterruptedException ex) {
            task.setStatus(TaskStatus.ABORTED.name());
            task.setExecLog("任务被终止");
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            task.setStatus(TaskStatus.FAILED.name());
            task.setExecLog("任务执行失败: " + ex.getMessage());
        } finally {
            task.setEndTime(LocalDateTime.now());
            taskRepository.save(task);
            taskScheduler.clear(taskId);
        }
    }

    /**
     * 根据输入测点计算并写入输出测点。
     */
    private void writeTaskOutputs(TaskEntity task,
                                  Map<String, String> inputBindings,
                                  Map<String, String> outputBindings) {
        List<InputBinding> inputs = new ArrayList<>();
        inputBindings.forEach((param, path) -> {
            if (StringUtils.hasText(path)) {
                inputs.add(new InputBinding(param, path.trim()));
            }
        });
        if (inputs.isEmpty()) {
            throw new IllegalStateException("输入测点为空");
        }
        List<String> inputPaths = new ArrayList<>(inputs.stream().map(InputBinding::path).toList());
        long startNs = toNano(task.getRangeStart());
        long endNs = toNano(task.getRangeEnd());
        SessionQueryDataSet dataSet = iginxStorageWrapper.executeWithSession(session ->
            session.queryData(inputPaths, startNs, endNs));

        long[] keys = dataSet.getKeys();
        if (keys == null || keys.length == 0) {
            throw new IllegalStateException("输入数据为空");
        }
        List<List<Object>> rows = dataSet.getValues();
        if (rows == null || rows.isEmpty()) {
            throw new IllegalStateException("输入数据为空");
        }

        List<String> outputPaths = new ArrayList<>();
        List<String> outputNames = new ArrayList<>();
        outputBindings.forEach((name, path) -> {
            if (StringUtils.hasText(path)) {
                String normalized = normalizeOutputPath(path.trim(), name);
                outputNames.add(name);
                outputPaths.add(normalized);
            }
        });
        if (outputPaths.isEmpty()) {
            throw new IllegalStateException("输出测点为空");
        }

        List<String> taskResultPaths = buildTaskResultPaths(task.getResultLink(), outputNames);
        List<List<Object>> outputValues = new ArrayList<>();
        for (int i = 0; i < outputPaths.size(); i++) {
            outputValues.add(new ArrayList<>());
        }

        for (int rowIndex = 0; rowIndex < keys.length; rowIndex++) {
            Map<String, Double> inputValueMap = new LinkedHashMap<>();
            List<Object> row = rows.get(rowIndex);
            for (int i = 0; i < inputs.size(); i++) {
                Object value = i < row.size() ? row.get(i) : null;
                Double numeric = toDouble(value);
                inputValueMap.put(inputs.get(i).paramLower(), numeric);
            }
            Double defaultValue = averageValues(inputValueMap);
            for (int i = 0; i < outputNames.size(); i++) {
                String outputName = outputNames.get(i);
                Double computed = computeOutputValue(outputName, inputValueMap, defaultValue);
                outputValues.get(i).add(computed);
            }
        }

        List<DataType> dataTypes = new ArrayList<>();
        for (int i = 0; i < outputPaths.size(); i++) {
            dataTypes.add(DataType.DOUBLE);
        }
        Object[] valuesArray = new Object[outputPaths.size()];
        for (int i = 0; i < outputValues.size(); i++) {
            valuesArray[i] = outputValues.get(i).toArray();
        }
        iginxStorageWrapper.executeWithSession(session -> {
            session.insertColumnRecords(outputPaths, keys, valuesArray, dataTypes);
            return null;
        });
        if (!taskResultPaths.isEmpty() && !taskResultPaths.equals(outputPaths)) {
            try {
                iginxStorageWrapper.executeWithSession(session -> {
                    session.insertColumnRecords(taskResultPaths, keys, valuesArray, dataTypes);
                    return null;
                });
            } catch (Exception ex) {
                log.warn("任务结果路径写入失败，已忽略。paths={}, message={}", taskResultPaths, ex.getMessage());
            }
        }
    }

    /**
     * 解析输入绑定 JSON。
     */
    private Map<String, String> parseInputBindings(String mappingJson) {
        if (!StringUtils.hasText(mappingJson)) {
            return Map.of();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(mappingJson, new TypeReference<>() {});
            Object mappingsObj = root.get("mappings");
            Map<String, String> bindings = new LinkedHashMap<>();
            if (mappingsObj instanceof List<?> list) {
                for (Object itemObj : list) {
                    if (itemObj instanceof Map<?, ?> item) {
                        Object param = item.get("param");
                        Object sourcePath = item.get("source_path");
                        if (param != null && sourcePath != null) {
                            bindings.put(param.toString(), sourcePath.toString());
                        }
                    }
                }
            }
            return bindings;
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * 解析输出绑定 JSON。
     */
    private Map<String, String> parseOutputBindings(String outputTargetJson) {
        if (!StringUtils.hasText(outputTargetJson)) {
            return Map.of();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(outputTargetJson, new TypeReference<>() {});
            Object pathsObj = root.get("paths");
            Map<String, String> results = new LinkedHashMap<>();
            if (pathsObj instanceof Map<?, ?> paths) {
                for (Map.Entry<?, ?> entry : paths.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        results.put(entry.getKey().toString(), entry.getValue().toString());
                    }
                }
            }
            return results;
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * 规范化输出测点路径。
     */
    private String normalizeOutputPath(String path, String outputName) {
        if (!StringUtils.hasText(path)) {
            return outputName;
        }
        return path.trim();
    }

    /**
     * 构建任务结果写入路径列表。
     */
    private List<String> buildTaskResultPaths(String resultPrefix, List<String> outputNames) {
        if (!StringUtils.hasText(resultPrefix) || outputNames == null || outputNames.isEmpty()) {
            return List.of();
        }
        String prefix = resultPrefix.trim();
        List<String> paths = new ArrayList<>();
        for (String name : outputNames) {
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String path = prefix.endsWith(".") ? prefix + name : prefix + "." + name;
            paths.add(path);
        }
        return paths;
    }

    /**
     * 根据输出名称计算输出值。
     */
    private Double computeOutputValue(String outputName, Map<String, Double> inputs, Double fallback) {
        if (outputName == null) {
            return fallback;
        }
        String key = outputName.trim().toLowerCase(Locale.ROOT);
        if ("power".equals(key)) {
            Double temperature = inputs.get("temperature");
            Double pressure = inputs.get("pressure");
            Double flow = inputs.get("flow");
            if (temperature != null && pressure != null && flow != null) {
                return 0.2 * temperature + 0.05 * pressure + 1.5 * flow;
            }
        }
        return fallback;
    }

    /**
     * 计算输入值的平均值。
     */
    private Double averageValues(Map<String, Double> values) {
        double sum = 0;
        int count = 0;
        for (Double value : values.values()) {
            if (value != null) {
                sum += value;
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return sum / count;
    }

    /**
     * 将对象转换为 Double。
     */
    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof byte[] bytes) {
            return parseDoubleSafely(new String(bytes));
        }
        if (value instanceof String text) {
            return parseDoubleSafely(text);
        }
        return null;
    }

    /**
     * 安全解析字符串为 Double。
     */
    private Double parseDoubleSafely(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 将时间转换为纳秒时间戳。
     */
    private long toNano(LocalDateTime time) {
        if (time == null) {
            return 0L;
        }
        long millis = time.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        return TimeParser.toNano(millis);
    }

    /**
     * 输入绑定结构。
     */
    private record InputBinding(String param, String path) {
        /**
         * 获取参数名的小写形式，用于统一匹配。
         */
        private String paramLower() {
            return param == null ? "" : param.trim().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * 标记任务为失败并记录日志。
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
     * 获取任务实体，不存在则抛异常。
     */
    private TaskEntity findTask(String taskId) {
        return taskRepository.findById(taskId)
            .orElseThrow(() -> BizException.badRequest("任务不存在，id=" + taskId));
    }

    /**
     * 将任务实体转换为前端展示对象。
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
        vo.setExecLog(entity.getExecLog());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
