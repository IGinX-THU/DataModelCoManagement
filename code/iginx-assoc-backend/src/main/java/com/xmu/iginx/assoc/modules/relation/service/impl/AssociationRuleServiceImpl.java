package com.xmu.iginx.assoc.modules.relation.service.impl;

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
import com.xmu.iginx.assoc.modules.model.vo.ModelFunctionOptionVO;
import com.xmu.iginx.assoc.modules.relation.dto.AssociationRuleCreateRequest;
import com.xmu.iginx.assoc.modules.relation.dto.AssociationRuleUpdateRequest;
import com.xmu.iginx.assoc.modules.relation.entity.AssociationRuleEntity;
import com.xmu.iginx.assoc.modules.relation.repository.AssociationRuleRepository;
import com.xmu.iginx.assoc.modules.relation.service.AssociationRuleService;
import com.xmu.iginx.assoc.modules.relation.vo.AssociationRuleVO;
import com.xmu.iginx.assoc.modules.task.enums.TaskStatus;
import com.xmu.iginx.assoc.modules.task.repository.TaskRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关联规则服务实现。
 */
@Service
@RequiredArgsConstructor
public class AssociationRuleServiceImpl implements AssociationRuleService {

    private final AssociationRuleRepository associationRuleRepository;
    private final ModelAssetRepository modelAssetRepository;
    private final MetaModelProfileRepository profileRepository;
    private final ModelFileStorageService modelFileStorageService;
    private final ModelFunctionSchemaParser functionSchemaParser;
    private final ObjectMapper objectMapper;
    private final TaskRepository taskRepository;

    /**
     * 创建关联规则并持久化。
     *
     * @param request 创建参数
     * @return 新规则 ID
     */
    @Override
    @Transactional
    public Long createRule(AssociationRuleCreateRequest request) {
        ModelAssetEntity asset = findAsset(request.getModelId());
        String functionName = normalizeFunctionName(request.getFunctionName());
        ValidatedBindings validated = validateBindings(asset, functionName, request.getBindings(), request.getResults());

        AssociationRuleEntity entity = new AssociationRuleEntity();
        entity.setName(request.getName().trim());
        entity.setModelId(asset.getId());
        entity.setFunctionName(functionName);
        entity.setMappingJson(writeJson(buildMappingJson(functionName, validated)));
        entity.setOutputTarget(writeJson(buildOutputTarget(validated)));
        entity.setEnabled(request.getEnabled() != null ? request.getEnabled() : Boolean.TRUE);
        entity.setUpdateTime(LocalDateTime.now());

        AssociationRuleEntity saved = associationRuleRepository.save(entity);
        return saved.getId();
    }

    /**
     * 更新关联规则基本信息与绑定关系。
     *
     * @param ruleId 规则 ID
     * @param request 更新参数
     */
    @Override
    @Transactional
    public void updateRule(Long ruleId, AssociationRuleUpdateRequest request) {
        AssociationRuleEntity entity = findRule(ruleId);
        ModelAssetEntity asset = findAsset(entity.getModelId());
        String functionName = normalizeFunctionName(request.getFunctionName());
        ValidatedBindings validated = validateBindings(asset, functionName, request.getBindings(), request.getResults());

        entity.setName(request.getName().trim());
        entity.setFunctionName(functionName);
        entity.setMappingJson(writeJson(buildMappingJson(functionName, validated)));
        entity.setOutputTarget(writeJson(buildOutputTarget(validated)));
        entity.setUpdateTime(LocalDateTime.now());
        associationRuleRepository.save(entity);
    }

    /**
     * 更新规则启用状态。
     *
     * @param ruleId 规则 ID
     * @param enabled 是否启用
     */
    @Override
    @Transactional
    public void updateStatus(Long ruleId, boolean enabled) {
        AssociationRuleEntity entity = findRule(ruleId);
        entity.setEnabled(enabled);
        entity.setUpdateTime(LocalDateTime.now());
        associationRuleRepository.save(entity);
    }

    /**
     * 删除规则，若存在运行中任务则拒绝删除。
     *
     * @param ruleId 规则 ID
     */
    @Override
    @Transactional
    public void deleteRule(Long ruleId) {
        AssociationRuleEntity entity = findRule(ruleId);
        boolean hasRunning = taskRepository.existsByRuleIdAndStatusIn(ruleId,
            List.of(TaskStatus.PENDING.name(), TaskStatus.RUNNING.name()));
        if (hasRunning) {
            throw BizException.badRequest("规则存在运行中任务，无法删除");
        }
        // 删除规则前清理关联任务记录
        taskRepository.deleteByRuleId(ruleId);
        associationRuleRepository.delete(entity);
    }

    /**
     * 查询规则列表并转换为 VO。
     *
     * @return 规则列表
     */
    @Override
    public List<AssociationRuleVO> listRules() {
        List<AssociationRuleEntity> entities = associationRuleRepository.findAll();
        List<AssociationRuleVO> result = new ArrayList<>();
        for (AssociationRuleEntity entity : entities) {
            result.add(toVO(entity));
        }
        return result;
    }

    /**
     * 查询规则详情。
     *
     * @param ruleId 规则 ID
     * @return 规则详情
     */
    @Override
    public AssociationRuleVO getRule(Long ruleId) {
        return toVO(findRule(ruleId));
    }

    /**
     * 将实体转换为前端展示对象。
     *
     * @param entity 规则实体
     * @return 规则 VO
     */
    private AssociationRuleVO toVO(AssociationRuleEntity entity) {
        AssociationRuleVO vo = new AssociationRuleVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setModelId(entity.getModelId());
        vo.setFunctionName(entity.getFunctionName());
        vo.setEnabled(entity.getEnabled());
        vo.setUpdateTime(entity.getUpdateTime());

        MappingResult mappingResult = parseMapping(entity.getMappingJson());
        vo.setBindings(mappingResult.bindings());
        vo.setResults(mappingResult.results());

        ModelAssetEntity asset = modelAssetRepository.findById(entity.getModelId()).orElse(null);
        if (asset != null) {
            vo.setModelVersion(asset.getVersion());
            vo.setModelType(asset.getFileType());
            MetaModelProfileEntity profile = profileRepository.findById(asset.getProfileId()).orElse(null);
            if (profile != null) {
                vo.setModelName(profile.getName());
            }
        }
        return vo;
    }

    /**
     * 校验输入/输出绑定是否合法，并与模型 Schema 严格对齐。
     * <p>
     * 严格约束：
     * 1. 输入参数：名称/数量/方向必须与模型输入完全一致，且路径必须是 ts.* 或 rt.*；
     * 2. 输出参数：名称/数量/方向必须与模型输出完全一致，路径允许为空（为空时任务执行走默认路径）。
     * </p>
     *
     * @param asset 模型资产
     * @param functionName 目标函数名
     * @param bindings 输入绑定
     * @param results 输出绑定
     * @return 规范化后的绑定结果
     */
    private ValidatedBindings validateBindings(ModelAssetEntity asset,
                                               String functionName,
                                               Map<String, String> bindings,
                                               Map<String, String> results) {
        if (bindings == null || bindings.isEmpty()) {
            throw BizException.badRequest("请输入模型输入绑定");
        }
        if (results == null || results.isEmpty()) {
            throw BizException.badRequest("请输入模型输出绑定");
        }

        ModelIoSchema schema = resolveSchema(asset, functionName);
        List<ModelSchemaParam> inputParams = normalizeSchemaParams(schema.getInputs(), "输入");
        List<ModelSchemaParam> outputParams = normalizeSchemaParams(schema.getOutputs(), "输出");
        if (inputParams.isEmpty()) {
            throw BizException.badRequest("模型未定义输入参数，无法创建关联规则");
        }
        if (outputParams.isEmpty()) {
            throw BizException.badRequest("模型未定义输出参数，无法创建关联规则");
        }

        Map<String, String> normalizedInputs = normalizeBindingMap(bindings, "输入", false);
        Map<String, String> normalizedOutputs = normalizeBindingMap(results, "输出", true);

        ensureExactParamSet("输入", inputParams, normalizedInputs.keySet());
        ensureExactParamSet("输出", outputParams, normalizedOutputs.keySet());

        Map<String, String> orderedInputs = new LinkedHashMap<>();
        for (ModelSchemaParam param : inputParams) {
            String name = param.getName().trim();
            String path = normalizePath(normalizedInputs.get(name), "输入参数[" + name + "] 路径");
            if (!DataPrefixRules.startsWithPrefix(path, DataPrefixRules.TS_PREFIX)
                && !DataPrefixRules.startsWithPrefix(path, DataPrefixRules.RT_PREFIX)) {
                throw BizException.badRequest("输入参数[" + name + "] 路径必须以 ts 或 rt 开头");
            }
            orderedInputs.put(name, path);
        }

        Map<String, String> orderedOutputs = new LinkedHashMap<>();
        for (ModelSchemaParam param : outputParams) {
            String name = param.getName().trim();
            String raw = normalizedOutputs.get(name);
            if (!StringUtils.hasText(raw)) {
                // 允许留空，运行时会自动写入默认路径 task.result.<taskId>.<outputName>。
                orderedOutputs.put(name, "");
            } else {
                orderedOutputs.put(name, normalizePath(raw, "输出参数[" + name + "] 路径"));
            }
        }

        return new ValidatedBindings(orderedInputs, orderedOutputs, inputParams, outputParams);
    }

    /**
     * 获取指定函数的输入输出 Schema。
     *
     * @param asset 模型资产
     * @param functionName 函数名
     * @return Schema 描述
     */
    private ModelIoSchema resolveSchema(ModelAssetEntity asset, String functionName) {
        String fileType = normalizeModelFileType(asset.getFileType());
        if (!supportsFunctionBinding(fileType)) {
            throw BizException.badRequest("当前模型类型不支持函数级关联规则: " + fileType);
        }
        byte[] fileBytes = modelFileStorageService.readAsBytes(asset.getStoragePath(), asset.getFileSize());
        ensureFunctionExists(asset, fileBytes, fileType, functionName);
        try {
            return functionSchemaParser.parseByFunction(fileBytes, fileType, functionName).schema();
        } catch (IllegalArgumentException ex) {
            throw BizException.badRequest("函数解析失败: " + ex.getMessage());
        } catch (Exception ex) {
            throw BizException.badRequest("模型函数结构解析失败");
        }
    }

    /**
     * 构建绑定映射 JSON 结构。
     *
     * @param functionName 目标函数名
     * @param validated 已校验绑定
     * @return JSON 结构 Map
     */
    private Map<String, Object> buildMappingJson(String functionName, ValidatedBindings validated) {
        Map<String, String> inputTypes = buildTypeIndex(validated.inputParams());
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("function_name", functionName);
        List<Map<String, String>> mappings = new ArrayList<>();
        validated.inputBindings().forEach((key, value) -> {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("param", key);
            item.put("param_type", inputTypes.getOrDefault(key, "STRING"));
            item.put("direction", "INPUT");
            item.put("source_path", value);
            item.put("fill_strategy", "NONE");
            mappings.add(item);
        });
        root.put("mappings", mappings);
        root.put("output_target", buildOutputTarget(validated));
        return root;
    }

    /**
     * 构建输出映射结构。
     *
     * @param validated 已校验绑定
     * @return 输出结构 Map
     */
    private Map<String, Object> buildOutputTarget(ValidatedBindings validated) {
        Map<String, Object> outputTarget = new LinkedHashMap<>();
        outputTarget.put("paths", validated.outputBindings());
        List<Map<String, String>> metas = new ArrayList<>();
        Map<String, String> outputTypes = buildTypeIndex(validated.outputParams());
        validated.outputBindings().forEach((param, path) -> {
            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("param", param);
            meta.put("param_type", outputTypes.getOrDefault(param, "STRING"));
            meta.put("direction", "OUTPUT");
            metas.add(meta);
        });
        outputTarget.put("meta", metas);
        return outputTarget;
    }

    /**
     * 构建参数类型索引。
     */
    private Map<String, String> buildTypeIndex(List<ModelSchemaParam> params) {
        Map<String, String> types = new LinkedHashMap<>();
        for (ModelSchemaParam param : params) {
            if (param == null || !StringUtils.hasText(param.getName())) {
                continue;
            }
            types.put(param.getName().trim(), normalizeType(param.getType()));
        }
        return types;
    }

    /**
     * 规范化并校验绑定 map。
     */
    private Map<String, String> normalizeBindingMap(Map<String, String> raw,
                                                    String direction,
                                                    boolean allowEmptyPath) {
        if (raw == null || raw.isEmpty()) {
            throw BizException.badRequest(direction + "绑定不能为空");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            if (!StringUtils.hasText(key)) {
                throw BizException.badRequest(direction + "参数名不能为空");
            }
            if (result.containsKey(key)) {
                throw BizException.badRequest(direction + "参数重复: " + key);
            }
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (!allowEmptyPath && !StringUtils.hasText(value)) {
                throw BizException.badRequest(direction + "绑定路径不能为空: " + key);
            }
            result.put(key, value);
        }
        return result;
    }

    /**
     * 校验参数名称集合与 Schema 完全一致（名称/数量/方向）。
     */
    private void ensureExactParamSet(String direction, List<ModelSchemaParam> schemaParams, Set<String> actualParamNames) {
        Set<String> expected = schemaParams.stream()
            .map(ModelSchemaParam::getName)
            .filter(StringUtils::hasText)
            .map(String::trim)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> actual = actualParamNames == null
            ? Set.of()
            : actualParamNames.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (expected.equals(actual)) {
            return;
        }
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        Set<String> extra = new LinkedHashSet<>(actual);
        extra.removeAll(expected);
        throw BizException.badRequest(direction + "参数与模型结构不一致，缺失: " + missing + "，多余: " + extra);
    }

    /**
     * 规范化模型参数列表并校验重复参数名。
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
     * 规范化路径并校验非法字符。
     */
    private String normalizePath(String path, String fieldName) {
        String normalized = TimeSeriesPathUtils.normalizePath(path);
        if (!StringUtils.hasText(normalized)) {
            throw BizException.badRequest(fieldName + "不能为空");
        }
        if (containsIllegalPathChars(normalized)) {
            throw BizException.badRequest(fieldName + "包含非法字符");
        }
        if (normalized.contains("*")) {
            throw BizException.badRequest(fieldName + "不允许包含通配符");
        }
        return normalized;
    }

    /**
     * 判断路径是否含非法字符。
     */
    private boolean containsIllegalPathChars(String path) {
        return path.contains(";")
            || path.contains(" ")
            || path.contains("\t")
            || path.contains("\n")
            || path.contains("\r");
    }

    /**
     * 规范化函数名。
     */
    private String normalizeFunctionName(String functionName) {
        String normalized = functionName == null ? "" : functionName.trim();
        if (!StringUtils.hasText(normalized)) {
            throw BizException.badRequest("模型函数不能为空");
        }
        return normalized;
    }

    /**
     * 规范化模型文件类型。
     */
    private String normalizeModelFileType(String fileType) {
        if (!StringUtils.hasText(fileType)) {
            return "UNKNOWN";
        }
        String value = fileType.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "PYTHON", "PY" -> "PY";
            case "MATLAB", "MAT" -> "MAT";
            default -> value;
        };
    }

    /**
     * 判断是否支持函数级绑定。
     */
    private boolean supportsFunctionBinding(String fileType) {
        return "PY".equals(fileType) || "MAT".equals(fileType);
    }

    /**
     * 校验函数是否存在于模型版本中。
     */
    private void ensureFunctionExists(ModelAssetEntity asset, byte[] fileBytes, String fileType, String functionName) {
        List<ModelFunctionOptionVO> options = readFunctionsFromAsset(asset);
        if (options.isEmpty()) {
            options = parseFunctionsFromBytes(fileBytes, fileType);
        }
        boolean exists = options.stream()
            .map(ModelFunctionOptionVO::getName)
            .filter(StringUtils::hasText)
            .anyMatch(name -> name.trim().equals(functionName));
        if (!exists) {
            throw BizException.badRequest("未找到函数: " + functionName);
        }
    }

    /**
     * 读取模型版本缓存的函数列表。
     */
    private List<ModelFunctionOptionVO> readFunctionsFromAsset(ModelAssetEntity asset) {
        String json = asset.getFunctionList();
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<ModelFunctionOptionVO> list = objectMapper.readValue(json, new TypeReference<>() {});
            return list == null ? Collections.emptyList() : list.stream()
                .filter(item -> item != null && StringUtils.hasText(item.getName()))
                .collect(Collectors.toList());
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    /**
     * 从模型文件字节解析函数列表。
     */
    private List<ModelFunctionOptionVO> parseFunctionsFromBytes(byte[] fileBytes, String fileType) {
        return functionSchemaParser.listFunctions(fileBytes, fileType).stream()
            .map(meta -> {
                ModelFunctionOptionVO vo = new ModelFunctionOptionVO();
                vo.setName(meta.name());
                vo.setDisplayName(meta.displayName());
                vo.setSignature(meta.signature());
                vo.setLineNumber(meta.lineNumber());
                return vo;
            })
            .collect(Collectors.toList());
    }

    /**
     * 参数类型归一化。
     */
    private String normalizeType(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return "STRING";
        }
        String type = rawType.trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "DOUBLE", "FLOAT", "REAL", "DECIMAL", "SINGLE" -> "FLOAT";
            case "INT", "INTEGER", "LONG", "SHORT", "INT64", "INT32", "INT16", "INT8", "UINT8", "UINT16", "UINT32" -> "INT";
            case "BOOL", "BOOLEAN", "LOGICAL" -> "BOOLEAN";
            case "ARRAY" -> "ARRAY";
            case "OBJECT", "MAP", "DICT", "JSON" -> "OBJECT";
            case "STRING", "STR", "CHAR", "TEXT", "BINARY" -> "STRING";
            default -> "STRING";
        };
    }

    /**
     * 序列化为 JSON 字符串。
     */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw BizException.internal("关联规则保存失败");
        }
    }

    /**
     * 解析规则绑定 JSON。
     *
     * @param json 规则 JSON
     * @return 绑定解析结果
     */
    private MappingResult parseMapping(String json) {
        if (!StringUtils.hasText(json)) {
            return new MappingResult(Collections.emptyMap(), Collections.emptyMap());
        }
        try {
            Map<String, Object> root = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, String> bindings = new LinkedHashMap<>();
            Object mappingsObj = root.get("mappings");
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
            Map<String, String> results = new LinkedHashMap<>();
            Object outputObj = root.get("output_target");
            if (outputObj instanceof Map<?, ?> outputTarget) {
                Object pathsObj = outputTarget.get("paths");
                if (pathsObj instanceof Map<?, ?> paths) {
                    for (Map.Entry<?, ?> entry : paths.entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null) {
                            results.put(entry.getKey().toString(), entry.getValue().toString());
                        }
                    }
                }
            }
            return new MappingResult(bindings, results);
        } catch (Exception e) {
            return new MappingResult(Collections.emptyMap(), Collections.emptyMap());
        }
    }

    /**
     * 获取规则实体，不存在则抛出异常。
     */
    private AssociationRuleEntity findRule(Long id) {
        return associationRuleRepository.findById(id)
            .orElseThrow(() -> BizException.badRequest("关联规则不存在，id=" + id));
    }

    /**
     * 获取模型资产，不存在则抛出异常。
     */
    private ModelAssetEntity findAsset(Long id) {
        return modelAssetRepository.findById(id)
            .orElseThrow(() -> BizException.badRequest("模型版本不存在，id=" + id));
    }

    /**
     * 已校验绑定结果。
     */
    private record ValidatedBindings(Map<String, String> inputBindings,
                                     Map<String, String> outputBindings,
                                     List<ModelSchemaParam> inputParams,
                                     List<ModelSchemaParam> outputParams) {
    }

    /**
     * 绑定解析结果。
     */
    private record MappingResult(Map<String, String> bindings, Map<String, String> results) {
    }
}
