package com.xmu.iginx.assoc.modules.data.controller;

import com.xmu.iginx.assoc.common.Result;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.data.dto.DataExportRequest;
import com.xmu.iginx.assoc.modules.data.dto.MeasurementRequest;
import com.xmu.iginx.assoc.modules.data.dto.StorageGroupRequest;
import com.xmu.iginx.assoc.modules.data.dto.StructuredImportRequest;
import com.xmu.iginx.assoc.modules.data.dto.StructuredQueryRequest;
import com.xmu.iginx.assoc.modules.data.dto.StructuredRowCreateRequest;
import com.xmu.iginx.assoc.modules.data.dto.StructuredRowDeleteRequest;
import com.xmu.iginx.assoc.modules.data.dto.StructuredRowUpdateRequest;
import com.xmu.iginx.assoc.modules.data.dto.TableCreateRequest;
import com.xmu.iginx.assoc.modules.data.dto.TableDropRequest;
import com.xmu.iginx.assoc.modules.data.dto.TimeSeriesDeleteRequest;
import com.xmu.iginx.assoc.modules.data.dto.TimeSeriesImportRequest;
import com.xmu.iginx.assoc.modules.data.dto.TimeSeriesQueryRequest;
import com.xmu.iginx.assoc.modules.data.service.DataExportService;
import com.xmu.iginx.assoc.modules.data.service.DataImportService;
import com.xmu.iginx.assoc.modules.data.service.DataMaintainService;
import com.xmu.iginx.assoc.modules.data.service.DataQueryService;
import com.xmu.iginx.assoc.modules.data.service.StructureService;
import com.xmu.iginx.assoc.modules.data.util.DataFileStorageService;
import com.xmu.iginx.assoc.modules.data.vo.DataExportResultVO;
import com.xmu.iginx.assoc.modules.data.vo.DataImportResultVO;
import com.xmu.iginx.assoc.modules.data.vo.StructuredQueryResultVO;
import com.xmu.iginx.assoc.modules.data.vo.TimeSeriesQueryResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Tag(name = "Data Resource Operations")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/data")
public class DataResourceController {

    private final DataImportService dataImportService;
    private final DataExportService dataExportService;
    private final DataQueryService dataQueryService;
    private final DataMaintainService dataMaintainService;
    private final StructureService structureService;
    private final DataFileStorageService fileStorageService;

    @Operation(summary = "\u5bfc\u5165\u65f6\u5e8f\u6570\u636e")
    @PostMapping(value = "/import/ts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DataImportResultVO> importTimeSeries(@Valid @RequestPart("request") TimeSeriesImportRequest request,
                                                       @RequestPart("file") MultipartFile file) {
        return Result.success(dataImportService.importTimeSeries(request, file));
    }

    @Operation(summary = "\u5bfc\u5165\u7ed3\u6784\u5316\u6570\u636e")
    @PostMapping(value = "/import/struct", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DataImportResultVO> importStructured(@Valid @RequestPart("request") StructuredImportRequest request,
                                                       @RequestPart("file") MultipartFile file) {
        return Result.success(dataImportService.importStructured(request, file));
    }

    @Operation(summary = "\u5bfc\u51fa\u6570\u636e")
    @PostMapping("/export")
    public Result<DataExportResultVO> exportData(@Valid @RequestBody DataExportRequest request) {
        return Result.success(dataExportService.exportData(request));
    }

    @Operation(summary = "\u67e5\u8be2\u5bfc\u51fa\u4efb\u52a1")
    @GetMapping("/export/tasks/{taskId}")
    public Result<DataExportResultVO> exportTask(@PathVariable Long taskId) {
        return Result.success(dataExportService.queryExportTask(taskId));
    }

    @Operation(summary = "\u65f6\u5e8f\u6570\u636e\u67e5\u8be2")
    @PostMapping("/query/ts")
    public Result<TimeSeriesQueryResultVO> queryTimeSeries(@Valid @RequestBody TimeSeriesQueryRequest request) {
        return Result.success(dataQueryService.queryTimeSeries(request));
    }

    @Operation(summary = "\u7ed3\u6784\u5316\u6570\u636e\u67e5\u8be2")
    @PostMapping("/query/struct")
    public Result<StructuredQueryResultVO> queryStructured(@Valid @RequestBody StructuredQueryRequest request) {
        return Result.success(dataQueryService.queryStructured(request));
    }

    @Operation(summary = "\u65f6\u5e8f\u6570\u636e\u7ef4\u62a4")
    @PostMapping("/ts/delete")
    public Result<Void> deleteTimeSeries(@Valid @RequestBody TimeSeriesDeleteRequest request) {
        dataMaintainService.deleteTimeSeries(request);
        return Result.success();
    }

    @Operation(summary = "\u65b0\u589e\u7ed3\u6784\u5316\u6570\u636e")
    @PostMapping("/struct/rows")
    public Result<Void> createStructuredRow(@Valid @RequestBody StructuredRowCreateRequest request) {
        dataMaintainService.createStructuredRow(request);
        return Result.success();
    }

    @Operation(summary = "\u66f4\u65b0\u7ed3\u6784\u5316\u6570\u636e")
    @PutMapping("/struct/rows")
    public Result<Void> updateStructuredRow(@Valid @RequestBody StructuredRowUpdateRequest request) {
        dataMaintainService.updateStructuredRow(request);
        return Result.success();
    }

    @Operation(summary = "\u5220\u9664\u7ed3\u6784\u5316\u6570\u636e")
    @DeleteMapping("/struct/rows")
    public Result<Void> deleteStructuredRow(@Valid @RequestBody StructuredRowDeleteRequest request) {
        dataMaintainService.deleteStructuredRow(request);
        return Result.success();
    }

    @Operation(summary = "\u521b\u5efa\u5b58\u50a8\u7ec4")
    @PostMapping("/structures/storage-groups")
    public Result<Void> createStorageGroup(@Valid @RequestBody StorageGroupRequest request) {
        structureService.createStorageGroup(request);
        return Result.success();
    }

    @Operation(summary = "\u5220\u9664\u5b58\u50a8\u7ec4")
    @PostMapping("/structures/storage-groups/drop")
    public Result<Void> dropStorageGroup(@Valid @RequestBody StorageGroupRequest request) {
        structureService.dropStorageGroup(request);
        return Result.success();
    }

    @Operation(summary = "\u521b\u5efa\u6d4b\u70b9")
    @PostMapping("/structures/measurements")
    public Result<Void> createMeasurement(@Valid @RequestBody MeasurementRequest request) {
        structureService.createMeasurement(request);
        return Result.success();
    }

    @Operation(summary = "\u5220\u9664\u6d4b\u70b9")
    @PostMapping("/structures/measurements/drop")
    public Result<Void> dropMeasurement(@Valid @RequestBody MeasurementRequest request) {
        structureService.dropMeasurement(request);
        return Result.success();
    }

    @Operation(summary = "\u521b\u5efa\u8868")
    @PostMapping("/structures/tables")
    public Result<Void> createTable(@Valid @RequestBody TableCreateRequest request) {
        structureService.createTable(request);
        return Result.success();
    }

    @Operation(summary = "\u5220\u9664\u8868")
    @PostMapping("/structures/tables/drop")
    public Result<Void> dropTable(@Valid @RequestBody TableDropRequest request) {
        structureService.dropTable(request);
        return Result.success();
    }

    @Operation(summary = "\u4e0b\u8f7d\u5bfc\u51fa\u6587\u4ef6")
    @GetMapping("/files/{fileName}")
    public void downloadFile(@PathVariable String fileName, HttpServletResponse response) {
        if (fileName.contains("..")) {
            throw BizException.badRequest("非法文件路径");
        }
        Path path = fileStorageService.resolveFile(fileName);
        if (!path.startsWith(fileStorageService.resolveRoot())) {
            throw BizException.badRequest("非法文件路径");
        }
        if (!Files.exists(path)) {
            throw BizException.badRequest("文件不存在");
        }
        try {
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encoded + "\"");
            Files.copy(path, response.getOutputStream());
            response.flushBuffer();
        } catch (Exception ex) {
            throw BizException.internal("文件下载失败: " + ex.getMessage());
        }
    }
}
