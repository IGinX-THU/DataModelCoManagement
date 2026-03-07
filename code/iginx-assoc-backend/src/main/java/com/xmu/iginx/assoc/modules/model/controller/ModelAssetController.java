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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "Model Asset Management")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/models")
public class ModelAssetController {

    private final ModelAssetService modelAssetService;
    private final ModelFileStorageService fileStorageService;

    @Operation(summary = "模型列表")
    @GetMapping
    public Result<List<ModelProfileVO>> listProfiles() {
        return Result.success(modelAssetService.listProfiles());
    }

    @Operation(summary = "模型详情")
    @GetMapping("/{id}")
    public Result<ModelProfileVO> detail(@PathVariable Long id) {
        return Result.success(modelAssetService.getProfile(id));
    }

    @Operation(summary = "上传模型文件")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ModelProfileVO> upload(@Valid @ModelAttribute ModelUploadRequest request,
                                         @RequestPart("file") MultipartFile file) {
        return Result.success(modelAssetService.uploadModel(request, file));
    }

    @Operation(summary = "更新模型档案")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ModelProfileUpdateRequest request) {
        modelAssetService.updateProfile(id, request);
        return Result.success();
    }

    @Operation(summary = "删除模型档案")
    @DeleteMapping("/{id}")
    public Result<Void> deleteProfile(@PathVariable Long id) {
        modelAssetService.deleteProfile(id);
        return Result.success();
    }

    @Operation(summary = "删除模型版本")
    @DeleteMapping("/assets/{assetId}")
    public Result<Void> deleteVersion(@PathVariable Long assetId) {
        modelAssetService.deleteVersion(assetId);
        return Result.success();
    }

    @Operation(summary = "下载模型文件")
    @GetMapping("/assets/{assetId}/download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable Long assetId) {
        ModelAssetEntity asset = modelAssetService.getAsset(assetId);
        fileStorageService.ensureExists(asset.getStoragePath());
        String fileName = URLEncoder.encode(asset.getFileName(), StandardCharsets.UTF_8);
        StreamingResponseBody body = outputStream ->
            fileStorageService.writeTo(asset.getStoragePath(), asset.getFileSize(), outputStream);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(body);
    }

    @Operation(summary = "解析模型接口定义")
    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ModelVersionVO> parse(@RequestPart("file") MultipartFile file) {
        return Result.success(modelAssetService.parseSchema(file));
    }

    @PostMapping(value = "/parse/functions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<ModelFunctionOptionVO>> listFunctions(@RequestPart("file") MultipartFile file) {
        return Result.success(modelAssetService.listFunctions(file));
    }

    @PostMapping(value = "/parse/schema", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ModelSchemaParseVO> parseByFunction(@RequestPart("file") MultipartFile file,
                                                      @RequestPart("functionName") String functionName) {
        return Result.success(modelAssetService.parseSchemaByFunction(file, functionName));
    }
}
