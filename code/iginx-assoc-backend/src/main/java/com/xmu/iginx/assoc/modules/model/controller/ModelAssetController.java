package com.xmu.iginx.assoc.modules.model.controller;

import com.xmu.iginx.assoc.common.Result;
import com.xmu.iginx.assoc.modules.model.dto.ModelProfileUpdateRequest;
import com.xmu.iginx.assoc.modules.model.dto.ModelUploadRequest;
import com.xmu.iginx.assoc.modules.model.entity.ModelAssetEntity;
import com.xmu.iginx.assoc.modules.model.service.ModelAssetService;
import com.xmu.iginx.assoc.modules.model.util.ModelFileStorageService;
import com.xmu.iginx.assoc.modules.model.vo.ModelFunctionOptionVO;
import com.xmu.iginx.assoc.modules.model.vo.ModelProfileVO;
import com.xmu.iginx.assoc.modules.model.vo.ModelSchemaParseVO;
import com.xmu.iginx.assoc.modules.model.vo.ModelVersionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 模型资产接口，提供模型上传、解析、下载与管理能力。
 */
@Tag(name = "Model Asset Management")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/models")
public class ModelAssetController {

    private final ModelAssetService modelAssetService;
    private final ModelFileStorageService fileStorageService;

    /**
     * 获取模型列表。
     *
     * @return 模型列表
     */
    @Operation(summary = "模型列表")
    @GetMapping
    public Result<List<ModelProfileVO>> listProfiles() {
        return Result.success(modelAssetService.listProfiles());
    }

    /**
     * 获取模型详情。
     *
     * @param id 模型 ID
     * @return 模型档案
     */
    @Operation(summary = "模型详情")
    @GetMapping("/{id}")
    public Result<ModelProfileVO> detail(@PathVariable Long id) {
        return Result.success(modelAssetService.getProfile(id));
    }

    /**
     * 上传模型文件并创建档案。
     *
     * @param request 上传请求
     * @param file 模型文件
     * @return 模型档案
     */
    @Operation(summary = "上传模型文件")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ModelProfileVO> upload(@Valid @ModelAttribute ModelUploadRequest request,
                                         @RequestPart("file") MultipartFile file) {
        return Result.success(modelAssetService.uploadModel(request, file));
    }

    /**
     * 更新模型档案。
     *
     * @param id 模型 ID
     * @param request 更新请求
     * @return 操作结果
     */
    @Operation(summary = "更新模型档案")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ModelProfileUpdateRequest request) {
        modelAssetService.updateProfile(id, request);
        return Result.success();
    }

    /**
     * 删除模型档案。
     *
     * @param id 模型 ID
     * @return 操作结果
     */
    @Operation(summary = "删除模型档案")
    @DeleteMapping("/{id}")
    public Result<Void> deleteProfile(@PathVariable Long id) {
        modelAssetService.deleteProfile(id);
        return Result.success();
    }

    /**
     * 删除模型版本。
     *
     * @param assetId 版本 ID
     * @return 操作结果
     */
    @Operation(summary = "删除模型版本")
    @DeleteMapping("/assets/{assetId}")
    public Result<Void> deleteVersion(@PathVariable Long assetId) {
        modelAssetService.deleteVersion(assetId);
        return Result.success();
    }

    /**
     * 下载模型文件。
     *
     * @param assetId 版本 ID
     * @return 文件流响应
     */
    @Operation(summary = "下载模型文件")
    @GetMapping("/assets/{assetId}/download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable Long assetId) {
        ModelAssetEntity asset = modelAssetService.getAsset(assetId);
        fileStorageService.ensureExists(asset.getStoragePath());
        // 对文件名进行 URL 编码，避免中文乱码
        String fileName = URLEncoder.encode(asset.getFileName(), StandardCharsets.UTF_8);
        StreamingResponseBody body = outputStream ->
            fileStorageService.writeTo(asset.getStoragePath(), asset.getFileSize(), outputStream);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(body);
    }

    /**
     * 解析模型接口定义。
     *
     * @param file 模型文件
     * @return 解析结果
     */
    @Operation(summary = "解析模型接口定义")
    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ModelVersionVO> parse(@RequestPart("file") MultipartFile file) {
        return Result.success(modelAssetService.parseSchema(file));
    }

    /**
     * 解析模型可用函数列表。
     *
     * @param file 模型文件
     * @return 函数列表
     */
    @PostMapping(value = "/parse/functions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<ModelFunctionOptionVO>> listFunctions(@RequestPart("file") MultipartFile file) {
        return Result.success(modelAssetService.listFunctions(file));
    }

    /**
     * 按函数名解析模型输入输出 Schema。
     *
     * @param file 模型文件
     * @param functionName 函数名
     * @return 解析结果
     */
    @PostMapping(value = "/parse/schema", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ModelSchemaParseVO> parseByFunction(@RequestPart("file") MultipartFile file,
                                                      @RequestPart("functionName") String functionName) {
        return Result.success(modelAssetService.parseSchemaByFunction(file, functionName));
    }

    /**
     * 获取已上传模型版本可用函数列表。
     *
     * @param assetId 模型版本 ID
     * @return 函数列表
     */
    @GetMapping("/assets/{assetId}/functions")
    public Result<List<ModelFunctionOptionVO>> listFunctionsByAsset(@PathVariable Long assetId) {
        return Result.success(modelAssetService.listFunctionsByAsset(assetId));
    }

    /**
     * 按模型版本与函数名解析 Schema。
     *
     * @param assetId 模型版本 ID
     * @param functionName 函数名
     * @return 解析结果
     */
    @GetMapping("/assets/{assetId}/functions/schema")
    public Result<ModelSchemaParseVO> parseByAssetFunction(@PathVariable Long assetId,
                                                           @RequestParam("functionName") String functionName) {
        return Result.success(modelAssetService.parseSchemaByAssetFunction(assetId, functionName));
    }
}
