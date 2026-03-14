package com.xmu.iginx.assoc.modules.model.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.model.dto.ModelIoSchema;
import com.xmu.iginx.assoc.modules.model.dto.ModelProfileUpdateRequest;
import com.xmu.iginx.assoc.modules.model.dto.ModelSchemaParam;
import com.xmu.iginx.assoc.modules.model.dto.ModelUploadRequest;
import com.xmu.iginx.assoc.modules.model.entity.MetaModelProfileEntity;
import com.xmu.iginx.assoc.modules.model.entity.ModelAssetEntity;
import com.xmu.iginx.assoc.modules.model.repository.MetaModelProfileRepository;
import com.xmu.iginx.assoc.modules.model.repository.ModelAssetRepository;
import com.xmu.iginx.assoc.modules.model.service.ModelAssetService;
import com.xmu.iginx.assoc.modules.model.util.ModelFileStorageService;
import com.xmu.iginx.assoc.modules.model.util.ModelFunctionSchemaParser;
import com.xmu.iginx.assoc.modules.model.util.ModelSchemaParser;
import com.xmu.iginx.assoc.modules.model.vo.ModelFunctionOptionVO;
import com.xmu.iginx.assoc.modules.model.vo.ModelProfileVO;
import com.xmu.iginx.assoc.modules.model.vo.ModelSchemaParseVO;
import com.xmu.iginx.assoc.modules.model.vo.ModelVersionVO;
import com.xmu.iginx.assoc.modules.relation.repository.AssociationRuleRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 模型资产服务实现，负责档案管理、文件存储与结构解析。
 */
@Service
@RequiredArgsConstructor
public class ModelAssetServiceImpl implements ModelAssetService {

    private static final long MAX_FILE_SIZE = 500L * 1024 * 1024;
    private static final Set<String> TEXT_TYPES = Set.of("PY", "MAT", "AME", "CSV");
    private static final Set<String> ALLOWED_TYPES = Set.of("PY", "MAT", "AME", "DLL", "FMU", "ZIP", "CSV", "XLSX");

    private final MetaModelProfileRepository profileRepository;
    private final ModelAssetRepository assetRepository;
    private final AssociationRuleRepository associationRuleRepository;
    private final ModelFileStorageService fileStorageService;
    private final ModelSchemaParser schemaParser;
    private final ModelFunctionSchemaParser functionSchemaParser;
    private final ObjectMapper objectMapper;

    /**
     * 查询全部模型档案并转换为视图列表。
     *
     * @return 模型档案列表
     */
    @Override
    public List<ModelProfileVO> listProfiles() {
        List<MetaModelProfileEntity> profiles = profileRepository.findAll();
        List<ModelProfileVO> result = new ArrayList<>();
        for (MetaModelProfileEntity profile : profiles) {
            result.add(toProfileVO(profile));
        }
        return result;
    }

    /**
     * 根据档案 ID 获取模型档案视图。
     *
     * @param profileId 档案 ID
     * @return 模型档案视图
     */
    @Override
    public ModelProfileVO getProfile(Long profileId) {
        MetaModelProfileEntity profile = findProfile(profileId);
        return toProfileVO(profile);
    }

    /**
     * 上传模型文件并创建（或更新）模型档案。
     *
     * @param request 上传请求
     * @param file 模型文件
     * @return 模型档案视图
     */
    @Override
    @Transactional
    public ModelProfileVO uploadModel(ModelUploadRequest request, MultipartFile file) {
        validateFile(file);
        String fileType = normalizeType(request.getType());
        validateFileType(file, fileType);

        // 解析或补全结构定义
        ModelIoSchema ioSchema = resolveSchema(request, file, fileType);
        String ioSchemaJson = writeSchema(ioSchema);
        MetaModelProfileEntity profile = resolveProfile(request, file);

        // 只在请求提供时覆盖档案字段
        if (StringUtils.hasText(request.getName())) {
            profile.setName(request.getName().trim());
        }
        if (StringUtils.hasText(request.getDescription())) {
            profile.setDescription(request.getDescription().trim());
        }
        if (StringUtils.hasText(request.getDeveloper())) {
            profile.setDeveloper(request.getDeveloper().trim());
        }
        if (StringUtils.hasText(request.getUsageScope())) {
            profile.setUsageScope(request.getUsageScope().trim());
        }
        profile.setIoSchema(ioSchemaJson);
        profile.setUpdateTime(LocalDateTime.now());
        profile = profileRepository.save(profile);

        String version = resolveVersion(request, profile);
        if (assetRepository.existsByProfileIdAndVersion(profile.getId(), version)) {
            throw BizException.badRequest("该模型版本已存在，请更换版本号");
        }

        ModelFileStorageService.StoredFile storedFile = storeFile(file, fileType, profile.getId(), version);

        // 清理旧的最新标识
        List<ModelAssetEntity> existingAssets = assetRepository.findByProfileId(profile.getId());
        for (ModelAssetEntity asset : existingAssets) {
            asset.setIsLatest(false);
        }
        assetRepository.saveAll(existingAssets);

        // 创建新的版本记录
        ModelAssetEntity asset = new ModelAssetEntity();
        asset.setProfileId(profile.getId());
        asset.setFileName(storedFile.fileName());
        asset.setFileType(fileType);
        asset.setStoragePath(storedFile.storageUri());
        asset.setVersion(version);
        asset.setUploadTime(LocalDateTime.now());
        asset.setFileMd5(storedFile.md5());
        asset.setFileSize(file.getSize());
        asset.setIoSchema(ioSchemaJson);
        asset.setIsLatest(true);
        assetRepository.save(asset);

        return toProfileVO(profile);
    }

    /**
     * 更新模型档案基础信息与结构定义。
     *
     * @param profileId 档案 ID
     * @param request 更新请求
     */
    @Override
    @Transactional
    public void updateProfile(Long profileId, ModelProfileUpdateRequest request) {
        MetaModelProfileEntity profile = findProfile(profileId);
        String newName = request.getName().trim();
        // 如果名称发生变化，需要校验唯一性
        if (!newName.equals(profile.getName()) && profileRepository.existsByName(newName)) {
            throw BizException.badRequest("模型名称已存在，请更换名称");
        }
        profile.setName(newName);
        profile.setDescription(request.getDescription());
        profile.setDeveloper(request.getDeveloper());
        profile.setUsageScope(request.getUsageScope());
        if (StringUtils.hasText(request.getIoSchema())) {
            profile.setIoSchema(validateSchemaJson(request.getIoSchema()));
            // 同步最新版本的结构定义，便于前端展示
            assetRepository.findFirstByProfileIdAndIsLatestTrue(profileId).ifPresent(asset -> {
                asset.setIoSchema(profile.getIoSchema());
                assetRepository.save(asset);
            });
        }
        profile.setUpdateTime(LocalDateTime.now());
        profileRepository.save(profile);
    }

    /**
     * 删除模型档案及其所有版本。
     *
     * @param profileId 档案 ID
     */
    @Override
    @Transactional
    public void deleteProfile(Long profileId) {
        MetaModelProfileEntity profile = findProfile(profileId);
        List<ModelAssetEntity> assets = assetRepository.findByProfileId(profileId);
        if (!assets.isEmpty()) {
            List<Long> assetIds = assets.stream().map(ModelAssetEntity::getId).toList();
            boolean inUse = associationRuleRepository.existsByModelIdIn(assetIds);
            if (inUse) {
                throw BizException.badRequest("模型已被关联规则引用，无法删除");
            }
        }
        // 先删除磁盘文件，再清理数据库记录
        for (ModelAssetEntity asset : assets) {
            deleteStoredFile(asset.getStoragePath());
        }
        assetRepository.deleteAll(assets);
        profileRepository.delete(profile);
    }

    /**
     * 删除指定模型版本，并维护最新版本标识。
     *
     * @param assetId 版本 ID
     */
    @Override
    @Transactional
    public void deleteVersion(Long assetId) {
        ModelAssetEntity asset = findAsset(assetId);
        boolean inUse = associationRuleRepository.existsByModelId(assetId);
        if (inUse) {
            throw BizException.badRequest("该版本正在被关联规则使用，无法删除");
        }
        deleteStoredFile(asset.getStoragePath());
        assetRepository.delete(asset);

        List<ModelAssetEntity> remain = assetRepository.findByProfileIdOrderByUploadTimeAsc(asset.getProfileId());
        if (remain.isEmpty()) {
            // 版本全部删除后移除档案
            profileRepository.deleteById(asset.getProfileId());
            return;
        }
        // 更新最新版本标识
        ModelAssetEntity latest = remain.get(remain.size() - 1);
        latest.setIsLatest(true);
        assetRepository.save(latest);
    }

    /**
     * 解析模型的输入输出结构。
     *
     * @param file 模型文件
     * @return 解析后的结构信息
     */
    @Override
    public ModelVersionVO parseSchema(MultipartFile file) {
        validateFile(file);
        String fileType = normalizeType(getExtension(file.getOriginalFilename()));
        if (!TEXT_TYPES.contains(fileType)) {
            // 非文本类模型不解析结构
            ModelVersionVO vo = new ModelVersionVO();
            vo.setInputs(Collections.emptyList());
            vo.setOutputs(Collections.emptyList());
            return vo;
        }
        ModelIoSchema schema = parseSchemaFromFile(file);
        ModelVersionVO vo = new ModelVersionVO();
        vo.setInputs(defaultList(schema.getInputs()));
        vo.setOutputs(defaultList(schema.getOutputs()));
        return vo;
    }

    /**
     * 解析脚本类模型的函数列表。
     *
     * @param file 模型文件
     * @return 可用函数列表
     */
    @Override
    public List<ModelFunctionOptionVO> listFunctions(MultipartFile file) {
        validateFile(file);
        String fileType = normalizeType(getExtension(file.getOriginalFilename()));
        if (!"PY".equals(fileType) && !"MAT".equals(fileType)) {
            // 仅脚本类模型支持函数解析
            return Collections.emptyList();
        }
        byte[] fileBytes = readFileBytes(file);
        return functionSchemaParser.listFunctions(fileBytes, fileType).stream()
            .map(item -> {
                ModelFunctionOptionVO vo = new ModelFunctionOptionVO();
                vo.setName(item.name());
                vo.setDisplayName(item.displayName());
                vo.setSignature(item.signature());
                vo.setLineNumber(item.lineNumber());
                return vo;
            })
            .collect(Collectors.toList());
    }

    /**
     * 按函数名解析模型的输入输出结构。
     *
     * @param file 模型文件
     * @param functionName 函数名
     * @return 解析结果
     */
    @Override
    public ModelSchemaParseVO parseSchemaByFunction(MultipartFile file, String functionName) {
        validateFile(file);
        if (!StringUtils.hasText(functionName)) {
            throw BizException.badRequest("函数名不能为空");
        }
        String fileType = normalizeType(getExtension(file.getOriginalFilename()));
        ModelSchemaParseVO vo = new ModelSchemaParseVO();
        if (!"PY".equals(fileType) && !"MAT".equals(fileType)) {
            // 非脚本类模型直接返回空结构并说明原因
            vo.setInputs(Collections.emptyList());
            vo.setOutputs(Collections.emptyList());
            vo.setParseMode(ModelFunctionSchemaParser.PARSE_MODE_COMMENT_FALLBACK);
            vo.setMessage("该文件类型不支持按函数解析");
            return vo;
        }

        byte[] fileBytes = readFileBytes(file);
        List<ModelFunctionSchemaParser.FunctionMeta> functions = functionSchemaParser.listFunctions(fileBytes, fileType);
        boolean exists = functions.stream().anyMatch(item -> item.name().equals(functionName));
        if (!exists) {
            throw BizException.badRequest("未找到函数: " + functionName);
        }

        // 按指定函数解析输入输出结构
        ModelFunctionSchemaParser.ParseSchemaResult result =
            functionSchemaParser.parseByFunction(fileBytes, fileType, functionName);
        vo.setInputs(defaultList(result.schema().getInputs()));
        vo.setOutputs(defaultList(result.schema().getOutputs()));
        vo.setParseMode(result.parseMode());
        vo.setMessage(result.message());
        return vo;
    }

    /**
     * 获取模型版本实体。
     *
     * @param assetId 版本 ID
     * @return 版本实体
     */
    @Override
    public ModelAssetEntity getAsset(Long assetId) {
        return findAsset(assetId);
    }

    /**
     * 根据请求解析档案对象，支持新建或复用已有档案。
     *
     * @param request 上传请求
     * @param file 模型文件
     * @return 档案实体
     */
    private MetaModelProfileEntity resolveProfile(ModelUploadRequest request, MultipartFile file) {
        if (request.getProfileId() != null) {
            return findProfile(request.getProfileId());
        }
        String name = StringUtils.hasText(request.getName())
            ? request.getName().trim()
            : removeExtension(file.getOriginalFilename());
        if (!StringUtils.hasText(name)) {
            throw BizException.badRequest("模型名称不能为空");
        }
        MetaModelProfileEntity existing = profileRepository.findByName(name).orElse(null);
        if (existing != null) {
            return existing;
        }
        MetaModelProfileEntity profile = new MetaModelProfileEntity();
        profile.setName(name);
        profile.setUpdateTime(LocalDateTime.now());
        return profile;
    }

    /**
     * 解析版本号，支持自动生成版本。
     *
     * @param request 上传请求
     * @param profile 档案实体
     * @return 版本号
     */
    private String resolveVersion(ModelUploadRequest request, MetaModelProfileEntity profile) {
        String raw = request.getVersion();
        if (!StringUtils.hasText(raw)) {
            return buildNextVersion(profile.getId());
        }
        String trimmed = raw.trim();
        if ("AUTO".equalsIgnoreCase(trimmed)) {
            return buildNextVersion(profile.getId());
        }
        return trimmed;
    }

    /**
     * 基于最新版本生成下一个版本号。
     *
     * @param profileId 档案 ID
     * @return 新版本号
     */
    private String buildNextVersion(Long profileId) {
        List<ModelAssetEntity> assets = assetRepository.findByProfileIdOrderByUploadTimeAsc(profileId);
        if (assets.isEmpty()) {
            return "v1.0.0";
        }
        String latest = assets.get(assets.size() - 1).getVersion();
        String next = incrementVersion(latest);
        if (StringUtils.hasText(next)) {
            return next;
        }
        // 无法按语义版本解析时，退化为顺序号
        return "v" + (assets.size() + 1);
    }

    /**
     * 对语义版本号进行自增。
     *
     * @param version 原版本号
     * @return 递增后的版本号，无法解析时返回 null
     */
    private String incrementVersion(String version) {
        if (!StringUtils.hasText(version)) {
            return null;
        }
        String cleaned = version.trim();
        if (cleaned.toLowerCase(Locale.ROOT).startsWith("v")) {
            cleaned = cleaned.substring(1);
        }
        String[] parts = cleaned.split("\\.");
        if (parts.length == 0) {
            return null;
        }
        int[] nums = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                nums[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (nums.length >= 3) {
            nums[2] = nums[2] + 1;
        } else if (nums.length == 2) {
            nums[1] = nums[1] + 1;
        } else {
            nums[0] = nums[0] + 1;
        }
        StringBuilder builder = new StringBuilder("v");
        for (int i = 0; i < nums.length; i++) {
            if (i > 0) {
                builder.append('.');
            }
            builder.append(nums[i]);
        }
        return builder.toString();
    }

    /**
     * 解析结构定义：优先使用请求中的 JSON，否则从文件解析。
     *
     * @param request 上传请求
     * @param file 模型文件
     * @param fileType 文件类型
     * @return 结构定义
     */
    private ModelIoSchema resolveSchema(ModelUploadRequest request, MultipartFile file, String fileType) {
        if (StringUtils.hasText(request.getIoSchema())) {
            try {
                return objectMapper.readValue(request.getIoSchema(), ModelIoSchema.class);
            } catch (IOException e) {
                throw BizException.badRequest("IO Schema 解析失败，请检查 JSON 格式");
            }
        }
        if (TEXT_TYPES.contains(fileType)) {
            return parseSchemaFromFile(file);
        }
        ModelIoSchema schema = new ModelIoSchema();
        schema.setInputs(Collections.emptyList());
        schema.setOutputs(Collections.emptyList());
        schema.setDependencies(Collections.emptyList());
        return schema;
    }

    /**
     * 从文件内容解析结构定义。
     *
     * @param file 模型文件
     * @return 结构定义
     */
    private ModelIoSchema parseSchemaFromFile(MultipartFile file) {
        try {
            return schemaParser.parse(file.getBytes());
        } catch (IOException e) {
            throw BizException.internal("模型文件解析失败");
        }
    }

    /**
     * 读取文件字节数组。
     *
     * @param file 模型文件
     * @return 文件字节
     */
    private byte[] readFileBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw BizException.internal("读取模型文件失败");
        }
    }

    /**
     * 校验文件基本合法性。
     *
     * @param file 模型文件
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("模型文件不能为空");
        }
        if (!StringUtils.hasText(file.getOriginalFilename())) {
            throw BizException.badRequest("模型文件名不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw BizException.badRequest("模型文件大小超过限制 (500MB)");
        }
    }

    /**
     * 校验文件类型与后缀一致性。
     *
     * @param file 模型文件
     * @param fileType 归一化后的类型
     */
    private void validateFileType(MultipartFile file, String fileType) {
        if (!ALLOWED_TYPES.contains(fileType)) {
            throw BizException.badRequest("不支持的模型类型: " + fileType);
        }
        String extension = getExtension(file.getOriginalFilename());
        if (StringUtils.hasText(extension) && !extension.equalsIgnoreCase(fileType)) {
            throw BizException.badRequest("模型文件后缀与类型不匹配");
        }
    }

    /**
     * 保存模型文件到存储介质。
     *
     * @param file 模型文件
     * @param fileType 文件类型
     * @param profileId 档案 ID
     * @param version 版本号
     * @return 存储结果
     */
    private ModelFileStorageService.StoredFile storeFile(MultipartFile file, String fileType, Long profileId, String version) {
        try {
            return fileStorageService.store(file, fileType, profileId, version);
        } catch (IOException e) {
            throw BizException.internal("模型文件保存失败");
        }
    }

    /**
     * 删除已存储的模型文件。
     *
     * @param storagePath 存储路径
     */
    private void deleteStoredFile(String storagePath) {
        fileStorageService.delete(storagePath);
    }

    /**
     * 将档案实体转换为视图对象。
     *
     * @param profile 档案实体
     * @return 视图对象
     */
    private ModelProfileVO toProfileVO(MetaModelProfileEntity profile) {
        List<ModelAssetEntity> assets = assetRepository.findByProfileIdOrderByUploadTimeAsc(profile.getId());
        ModelAssetEntity latest = assets.stream().filter(asset -> Boolean.TRUE.equals(asset.getIsLatest())).findFirst()
            .orElseGet(() -> assets.isEmpty() ? null : assets.get(assets.size() - 1));

        ModelProfileVO vo = new ModelProfileVO();
        vo.setId(profile.getId());
        vo.setName(profile.getName());
        vo.setDescription(profile.getDescription());
        vo.setDeveloper(profile.getDeveloper());
        vo.setUsageScope(profile.getUsageScope());
        vo.setUpdateTime(profile.getUpdateTime());

        if (latest != null) {
            vo.setType(latest.getFileType());
            vo.setVersion(latest.getVersion());
            vo.setFileSize(latest.getFileSize());
            vo.setUploadTime(latest.getUploadTime());
        }

        List<ModelVersionVO> history = new ArrayList<>();
        for (ModelAssetEntity asset : assets) {
            history.add(toVersionVO(asset));
        }
        vo.setHistory(history);

        if (!assets.isEmpty()) {
            List<Long> assetIds = assets.stream().map(ModelAssetEntity::getId).toList();
            vo.setRefCount(associationRuleRepository.countByModelIdIn(assetIds));
        } else {
            vo.setRefCount(0L);
        }
        return vo;
    }

    /**
     * 将版本实体转换为版本视图。
     *
     * @param asset 版本实体
     * @return 版本视图
     */
    private ModelVersionVO toVersionVO(ModelAssetEntity asset) {
        ModelIoSchema schema = readSchema(asset.getIoSchema());
        ModelVersionVO version = new ModelVersionVO();
        version.setId(asset.getId());
        version.setVersion(asset.getVersion());
        version.setFileType(asset.getFileType());
        version.setFileSize(asset.getFileSize());
        version.setFileMd5(asset.getFileMd5());
        version.setUploadTime(asset.getUploadTime());
        version.setLatest(Boolean.TRUE.equals(asset.getIsLatest()));
        version.setInputs(defaultList(schema.getInputs()));
        version.setOutputs(defaultList(schema.getOutputs()));
        return version;
    }

    /**
     * 读取 JSON 结构定义，失败时返回空结构。
     *
     * @param json 结构 JSON
     * @return 结构定义
     */
    private ModelIoSchema readSchema(String json) {
        if (!StringUtils.hasText(json)) {
            return emptySchema();
        }
        try {
            return objectMapper.readValue(json, ModelIoSchema.class);
        } catch (IOException e) {
            return emptySchema();
        }
    }

    /**
     * 构造空结构对象。
     *
     * @return 空结构
     */
    private ModelIoSchema emptySchema() {
        ModelIoSchema schema = new ModelIoSchema();
        schema.setInputs(Collections.emptyList());
        schema.setOutputs(Collections.emptyList());
        schema.setDependencies(Collections.emptyList());
        return schema;
    }

    /**
     * 将空列表兜底为不可变空列表。
     *
     * @param list 原始列表
     * @return 兜底后的列表
     */
    private List<ModelSchemaParam> defaultList(List<ModelSchemaParam> list) {
        return list == null ? Collections.emptyList() : list;
    }

    /**
     * 将结构对象序列化为 JSON 字符串。
     *
     * @param schema 结构对象
     * @return JSON 字符串
     */
    private String writeSchema(ModelIoSchema schema) {
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            throw BizException.internal("模型结构序列化失败");
        }
    }

    /**
     * 归一化文件类型。
     *
     * @param rawType 原始类型
     * @return 归一化后的类型
     */
    private String normalizeType(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return "UNKNOWN";
        }
        String value = rawType.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "PYTHON", "PY" -> "PY";
            case "MATLAB", "MAT" -> "MAT";
            case "AMESIM", "AME" -> "AME";
            case "DLL" -> "DLL";
            case "FMU" -> "FMU";
            case "ZIP" -> "ZIP";
            case "CSV" -> "CSV";
            case "EXCEL", "XLSX" -> "XLSX";
            default -> value;
        };
    }

    /**
     * 获取文件后缀（大写）。
     *
     * @param fileName 文件名
     * @return 后缀名
     */
    private String getExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
    }

    /**
     * 去除文件后缀。
     *
     * @param fileName 文件名
     * @return 去除后缀的名称
     */
    private String removeExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    /**
     * 校验并返回合法的 IO Schema JSON。
     *
     * @param ioSchema 结构 JSON
     * @return 原 JSON
     */
    private String validateSchemaJson(String ioSchema) {
        try {
            objectMapper.readValue(ioSchema, ModelIoSchema.class);
            return ioSchema;
        } catch (IOException e) {
            throw BizException.badRequest("IO Schema 解析失败，请检查 JSON 格式");
        }
    }

    /**
     * 根据 ID 查询模型档案。
     *
     * @param id 档案 ID
     * @return 档案实体
     */
    private MetaModelProfileEntity findProfile(Long id) {
        return profileRepository.findById(id)
            .orElseThrow(() -> BizException.badRequest("模型档案不存在，id=" + id));
    }

    /**
     * 根据 ID 查询模型版本。
     *
     * @param assetId 版本 ID
     * @return 版本实体
     */
    private ModelAssetEntity findAsset(Long assetId) {
        return assetRepository.findById(assetId)
            .orElseThrow(() -> BizException.badRequest("模型版本不存在，id=" + assetId));
    }
}
