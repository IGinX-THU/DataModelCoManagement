package com.xmu.iginx.assoc.modules.relation.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.model.dto.ModelIoSchema;
import com.xmu.iginx.assoc.modules.model.dto.ModelSchemaParam;
import com.xmu.iginx.assoc.modules.model.entity.MetaModelProfileEntity;
import com.xmu.iginx.assoc.modules.model.entity.ModelAssetEntity;
import com.xmu.iginx.assoc.modules.model.repository.MetaModelProfileRepository;
import com.xmu.iginx.assoc.modules.model.repository.ModelAssetRepository;
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
import java.util.List;
import java.util.Map;

/**
 * 关联规则服务实现。
 */
@Service
@RequiredArgsConstructor
public class AssociationRuleServiceImpl implements AssociationRuleService {

    private final AssociationRuleRepository associationRuleRepository;
    private final ModelAssetRepository modelAssetRepository;
    private final MetaModelProfileRepository profileRepository;
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
        validateBindings(asset, request.getBindings(), request.getResults());

        AssociationRuleEntity entity = new AssociationRuleEntity();
        entity.setName(request.getName().trim());
        entity.setModelId(asset.getId());
        entity.setDataId(request.getDataId());
        entity.setTriggerType(defaultValue(request.getTriggerType(), "MANUAL"));
        entity.setCronExp(request.getCronExp());
        entity.setMappingJson(writeJson(buildMappingJson(request.getBindings(), request.getResults())));
        entity.setOutputTarget(writeJson(buildOutputTarget(request.getResults())));
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
        validateBindings(asset, request.getBindings(), request.getResults());

        entity.setName(request.getName().trim());
        entity.setTriggerType(defaultValue(request.getTriggerType(), entity.getTriggerType()));
        entity.setCronExp(request.getCronExp());
        entity.setMappingJson(writeJson(buildMappingJson(request.getBindings(), request.getResults())));
        entity.setOutputTarget(writeJson(buildOutputTarget(request.getResults())));
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
        vo.setDataId(entity.getDataId());
        vo.setTriggerType(entity.getTriggerType());
        vo.setCronExp(entity.getCronExp());
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
     * 校验输入/输出绑定是否合法，并与模型 Schema 对齐。
     *
     * @param asset 模型资产
     * @param bindings 输入绑定
     * @param results 输出绑定
     */
    private void validateBindings(ModelAssetEntity asset, Map<String, String> bindings, Map<String, String> results) {
        if (bindings == null || bindings.isEmpty()) {
            throw BizException.badRequest("请输入模型输入绑定");
        }
        if (results == null || results.isEmpty()) {
            throw BizException.badRequest("请输入模型输出绑定");
        }
        bindings.forEach((key, value) -> {
            if (!StringUtils.hasText(value)) {
                throw BizException.badRequest("输入绑定路径不能为空: " + key);
            }
        });
        results.forEach((key, value) -> {
            if (!StringUtils.hasText(value)) {
                throw BizException.badRequest("输出绑定路径不能为空: " + key);
            }
        });
        ModelIoSchema schema = resolveSchema(asset);
        if (schema.getInputs() != null) {
            for (ModelSchemaParam param : schema.getInputs()) {
                if (Boolean.TRUE.equals(param.getRequired())) {
                    String value = bindings.get(param.getName());
                    if (!StringUtils.hasText(value)) {
                        throw BizException.badRequest("缺少必填输入参数: " + param.getName());
                    }
                }
            }
        }
        if (schema.getOutputs() != null) {
            for (ModelSchemaParam param : schema.getOutputs()) {
                String value = results.get(param.getName());
                if (!StringUtils.hasText(value)) {
                    throw BizException.badRequest("缺少输出绑定参数: " + param.getName());
                }
            }
        }
    }

    /**
     * 获取模型输入输出 Schema，优先使用资产内 Schema。
     *
     * @param asset 模型资产
     * @return Schema 描述
     */
    private ModelIoSchema resolveSchema(ModelAssetEntity asset) {
        String json = asset.getIoSchema();
        if (!StringUtils.hasText(json)) {
            MetaModelProfileEntity profile = profileRepository.findById(asset.getProfileId()).orElse(null);
            json = profile != null ? profile.getIoSchema() : null;
        }
        if (!StringUtils.hasText(json)) {
            return emptySchema();
        }
        try {
            return objectMapper.readValue(json, ModelIoSchema.class);
        } catch (Exception e) {
            return emptySchema();
        }
    }

    /**
     * 构造空 Schema。
     */
    private ModelIoSchema emptySchema() {
        ModelIoSchema schema = new ModelIoSchema();
        schema.setInputs(Collections.emptyList());
        schema.setOutputs(Collections.emptyList());
        return schema;
    }

    /**
     * 构建绑定映射 JSON 结构。
     *
     * @param bindings 输入绑定
     * @param results 输出绑定
     * @return JSON 结构 Map
     */
    private Map<String, Object> buildMappingJson(Map<String, String> bindings, Map<String, String> results) {
        Map<String, Object> root = new LinkedHashMap<>();
        List<Map<String, String>> mappings = new ArrayList<>();
        bindings.forEach((key, value) -> {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("param", key);
            item.put("source_path", value);
            item.put("fill_strategy", "NONE");
            mappings.add(item);
        });
        root.put("mappings", mappings);
        root.put("output_target", buildOutputTarget(results));
        return root;
    }

    /**
     * 构建输出映射结构。
     *
     * @param results 输出绑定
     * @return 输出结构 Map
     */
    private Map<String, Object> buildOutputTarget(Map<String, String> results) {
        Map<String, Object> outputTarget = new LinkedHashMap<>();
        outputTarget.put("paths", results);
        return outputTarget;
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
     * value 为空则返回默认值。
     */
    private String defaultValue(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
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
     * 绑定解析结果。
     */
    private record MappingResult(Map<String, String> bindings, Map<String, String> results) {
    }
}
