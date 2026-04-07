package com.xmu.iginx.assoc.modules.taskchain.service.impl;

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
import com.xmu.iginx.assoc.modules.taskchain.model.TaskChainRuleDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务链规则解析器。
 */
@Component
@RequiredArgsConstructor
public class TaskChainRuleResolver {

    private final AssociationRuleRepository associationRuleRepository;
    private final ModelAssetRepository modelAssetRepository;
    private final MetaModelProfileRepository profileRepository;
    private final ModelFileStorageService modelFileStorageService;
    private final ModelFunctionSchemaParser functionSchemaParser;
    private final ObjectMapper objectMapper;

    /**
     * 按规则 ID 解析任务链规则描述。
     */
    public TaskChainRuleDescriptor resolveDescriptor(Long ruleId) {
        AssociationRuleEntity rule = associationRuleRepository.findById(ruleId)
            .orElseThrow(() -> BizException.badRequest("关联规则不存在，id=" + ruleId));
        ModelAssetEntity asset = modelAssetRepository.findById(rule.getModelId())
            .orElseThrow(() -> BizException.badRequest("模型版本不存在，id=" + rule.getModelId()));
        return resolveDescriptor(rule, asset);
    }

    /**
     * 解析规则描述。
     */
    public TaskChainRuleDescriptor resolveDescriptor(AssociationRuleEntity rule, ModelAssetEntity asset) {
        String functionName = resolveFunctionName(rule);
        ModelIoSchema schema = resolveRuntimeSchema(asset, functionName);
        List<ModelSchemaParam> inputParams = normalizeSchemaParams(schema.getInputs(), "输入");
        List<ModelSchemaParam> outputParams = normalizeSchemaParams(schema.getOutputs(), "输出");
        if (inputParams.isEmpty()) {
            throw BizException.badRequest("模型未定义输入参数，无法用于任务链");
        }
        if (outputParams.isEmpty()) {
            throw BizException.badRequest("模型未定义输出参数，无法用于任务链");
        }

        List<RuleBinding> inputBindings = parseInputBindings(rule.getMappingJson());
        ensureExactBindings("输入", inputParams, inputBindings, "INPUT");
        String chainMode = resolveChainMode(inputBindings);

        TaskChainRuleDescriptor descriptor = new TaskChainRuleDescriptor();
        descriptor.setRuleId(rule.getId());
        descriptor.setRuleName(rule.getName());
        descriptor.setEnabled(rule.getEnabled());
        descriptor.setModelId(asset.getId());
        descriptor.setModelName(resolveModelName(asset));
        descriptor.setModelVersion(asset.getVersion());
        descriptor.setModelType(normalizeModelType(asset.getFileType()));
        descriptor.setModelFileName(asset.getFileName());
        descriptor.setModelStoragePath(asset.getStoragePath());
        descriptor.setModelFileSize(asset.getFileSize());
        descriptor.setFunctionName(functionName);
        descriptor.setChainMode(chainMode);
        descriptor.setInputs(buildInputDescriptors(inputParams, inputBindings));
        descriptor.setOutputs(buildOutputDescriptors(outputParams));
        return descriptor;
    }

    /**
     * 解析模型名称。
     */
    private String resolveModelName(ModelAssetEntity asset) {
        if (asset == null || asset.getProfileId() == null) {
            return "";
        }
        return profileRepository.findById(asset.getProfileId())
            .map(MetaModelProfileEntity::getName)
            .orElse("");
    }

    /**
     * 解析链模式。
     */
    private String resolveChainMode(List<RuleBinding> inputBindings) {
        Set<String> pathKinds = inputBindings.stream()
            .map(RuleBinding::pathKind)
            .filter(StringUtils::hasText)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (pathKinds.size() != 1) {
            throw BizException.badRequest("任务链仅支持输入类型一致的规则，不能混用 ts 与 rt 路径");
        }
        return "TS".equals(pathKinds.iterator().next()) ? "TIME_SERIES" : "STRUCTURED";
    }

    /**
     * 构建输入参数描述。
     */
    private List<TaskChainRuleDescriptor.ParamDescriptor> buildInputDescriptors(List<ModelSchemaParam> schemaParams,
                                                                                List<RuleBinding> bindings) {
        Map<String, RuleBinding> bindingIndex = bindings.stream()
            .collect(Collectors.toMap(RuleBinding::name, item -> item, (left, right) -> left, LinkedHashMap::new));
        List<TaskChainRuleDescriptor.ParamDescriptor> result = new ArrayList<>();
        for (ModelSchemaParam param : schemaParams) {
            RuleBinding binding = bindingIndex.get(param.getName());
            TaskChainRuleDescriptor.ParamDescriptor item = new TaskChainRuleDescriptor.ParamDescriptor();
            item.setName(param.getName());
            item.setType(normalizeType(param.getType()));
            item.setDefaultPath(binding == null ? "" : binding.path());
            item.setPathKind(binding == null ? "" : binding.pathKind());
            result.add(item);
        }
        return result;
    }

    /**
     * 构建输出参数描述。
     */
    private List<TaskChainRuleDescriptor.ParamDescriptor> buildOutputDescriptors(List<ModelSchemaParam> schemaParams) {
        List<TaskChainRuleDescriptor.ParamDescriptor> result = new ArrayList<>();
        for (ModelSchemaParam param : schemaParams) {
            TaskChainRuleDescriptor.ParamDescriptor item = new TaskChainRuleDescriptor.ParamDescriptor();
            item.setName(param.getName());
            item.setType(normalizeType(param.getType()));
            item.setDefaultPath("");
            item.setPathKind("");
            result.add(item);
        }
        return result;
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
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.badRequest("关联规则输入绑定解析失败");
        }
    }

    /**
     * 校验绑定与模型结构一致。
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
            String schemaType = normalizeType(schemaParam.getType());
            if (StringUtils.hasText(binding.type()) && !schemaType.equals(binding.type())) {
                throw BizException.badRequest(direction + "参数[" + schemaName + "] 类型与模型结构不一致");
            }
        }
    }

    /**
     * 按名称索引绑定。
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
     * 解析函数名。
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
     * 解析运行时函数结构。
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
        if (!StringUtils.hasText(ioSchemaJson) && asset.getProfileId() != null) {
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
     * 标准化模型参数列表。
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
     * 输入路径类别。
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
     * 标准化路径。
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
     * 类型标准化。
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
            default -> "STRING";
        };
    }

    /**
     * 方向标准化。
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
     * 模型类型标准化。
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
     * 安全裁剪字符串。
     */
    private String safeTrim(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    /**
     * 规则绑定。
     */
    private record RuleBinding(String name, String type, String direction, String path, String pathKind) {
    }
}
