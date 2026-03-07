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

    @Override
    public List<ModelProfileVO> listProfiles() {
        List<MetaModelProfileEntity> profiles = profileRepository.findAll();
        List<ModelProfileVO> result = new ArrayList<>();
        for (MetaModelProfileEntity profile : profiles) {
            result.add(toProfileVO(profile));
        }
        return result;
    }

    @Override
    public ModelProfileVO getProfile(Long profileId) {
        MetaModelProfileEntity profile = findProfile(profileId);
        return toProfileVO(profile);
    }

    @Override
    @Transactional
    public ModelProfileVO uploadModel(ModelUploadRequest request, MultipartFile file) {
        validateFile(file);
        String fileType = normalizeType(request.getType());
        validateFileType(file, fileType);

        ModelIoSchema ioSchema = resolveSchema(request, file, fileType);
        String ioSchemaJson = writeSchema(ioSchema);
        MetaModelProfileEntity profile = resolveProfile(request, file);

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

        List<ModelAssetEntity> existingAssets = assetRepository.findByProfileId(profile.getId());
        for (ModelAssetEntity asset : existingAssets) {
            asset.setIsLatest(false);
        }
        assetRepository.saveAll(existingAssets);

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

    @Override
    @Transactional
    public void updateProfile(Long profileId, ModelProfileUpdateRequest request) {
        MetaModelProfileEntity profile = findProfile(profileId);
        String newName = request.getName().trim();
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
        for (ModelAssetEntity asset : assets) {
            deleteStoredFile(asset.getStoragePath());
        }
        assetRepository.deleteAll(assets);
        profileRepository.delete(profile);
    }

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
            profileRepository.deleteById(asset.getProfileId());
            return;
        }
        ModelAssetEntity latest = remain.get(remain.size() - 1);
        latest.setIsLatest(true);
        assetRepository.save(latest);
    }

    @Override
    public ModelVersionVO parseSchema(MultipartFile file) {
        validateFile(file);
        String fileType = normalizeType(getExtension(file.getOriginalFilename()));
        if (!TEXT_TYPES.contains(fileType)) {
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

    @Override
    public List<ModelFunctionOptionVO> listFunctions(MultipartFile file) {
        validateFile(file);
        String fileType = normalizeType(getExtension(file.getOriginalFilename()));
        if (!"PY".equals(fileType) && !"MAT".equals(fileType)) {
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

    @Override
    public ModelSchemaParseVO parseSchemaByFunction(MultipartFile file, String functionName) {
        validateFile(file);
        if (!StringUtils.hasText(functionName)) {
            throw BizException.badRequest("Function name cannot be empty");
        }
        String fileType = normalizeType(getExtension(file.getOriginalFilename()));
        ModelSchemaParseVO vo = new ModelSchemaParseVO();
        if (!"PY".equals(fileType) && !"MAT".equals(fileType)) {
            vo.setInputs(Collections.emptyList());
            vo.setOutputs(Collections.emptyList());
            vo.setParseMode(ModelFunctionSchemaParser.PARSE_MODE_COMMENT_FALLBACK);
            vo.setMessage("Unsupported file type for function parsing");
            return vo;
        }

        byte[] fileBytes = readFileBytes(file);
        List<ModelFunctionSchemaParser.FunctionMeta> functions = functionSchemaParser.listFunctions(fileBytes, fileType);
        boolean exists = functions.stream().anyMatch(item -> item.name().equals(functionName));
        if (!exists) {
            throw BizException.badRequest("Function not found: " + functionName);
        }

        ModelFunctionSchemaParser.ParseSchemaResult result =
            functionSchemaParser.parseByFunction(fileBytes, fileType, functionName);
        vo.setInputs(defaultList(result.schema().getInputs()));
        vo.setOutputs(defaultList(result.schema().getOutputs()));
        vo.setParseMode(result.parseMode());
        vo.setMessage(result.message());
        return vo;
    }

    @Override
    public ModelAssetEntity getAsset(Long assetId) {
        return findAsset(assetId);
    }

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
        return "v" + (assets.size() + 1);
    }

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

    private ModelIoSchema parseSchemaFromFile(MultipartFile file) {
        try {
            return schemaParser.parse(file.getBytes());
        } catch (IOException e) {
            throw BizException.internal("模型文件解析失败");
        }
    }

    private byte[] readFileBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw BizException.internal("Failed to read model file");
        }
    }

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

    private void validateFileType(MultipartFile file, String fileType) {
        if (!ALLOWED_TYPES.contains(fileType)) {
            throw BizException.badRequest("不支持的模型类型: " + fileType);
        }
        String extension = getExtension(file.getOriginalFilename());
        if (StringUtils.hasText(extension) && !extension.equalsIgnoreCase(fileType)) {
            throw BizException.badRequest("模型文件后缀与类型不匹配");
        }
    }

    private ModelFileStorageService.StoredFile storeFile(MultipartFile file, String fileType, Long profileId, String version) {
        try {
            return fileStorageService.store(file, fileType, profileId, version);
        } catch (IOException e) {
            throw BizException.internal("模型文件保存失败");
        }
    }

    private void deleteStoredFile(String storagePath) {
        fileStorageService.delete(storagePath);
    }

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

    private ModelIoSchema emptySchema() {
        ModelIoSchema schema = new ModelIoSchema();
        schema.setInputs(Collections.emptyList());
        schema.setOutputs(Collections.emptyList());
        schema.setDependencies(Collections.emptyList());
        return schema;
    }

    private List<ModelSchemaParam> defaultList(List<ModelSchemaParam> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private String writeSchema(ModelIoSchema schema) {
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            throw BizException.internal("模型结构序列化失败");
        }
    }

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

    private String getExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
    }

    private String removeExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    private String validateSchemaJson(String ioSchema) {
        try {
            objectMapper.readValue(ioSchema, ModelIoSchema.class);
            return ioSchema;
        } catch (IOException e) {
            throw BizException.badRequest("IO Schema 解析失败，请检查 JSON 格式");
        }
    }

    private MetaModelProfileEntity findProfile(Long id) {
        return profileRepository.findById(id)
            .orElseThrow(() -> BizException.badRequest("模型档案不存在，id=" + id));
    }

    private ModelAssetEntity findAsset(Long assetId) {
        return assetRepository.findById(assetId)
            .orElseThrow(() -> BizException.badRequest("模型版本不存在，id=" + assetId));
    }
}
