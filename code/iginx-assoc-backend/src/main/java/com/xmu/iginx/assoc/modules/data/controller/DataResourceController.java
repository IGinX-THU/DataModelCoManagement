package com.xmu.iginx.assoc.modules.data.controller;

import com.xmu.iginx.assoc.common.Result;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.data.dto.DataColumnsDeleteRequest;
import com.xmu.iginx.assoc.modules.data.dto.DataExportRequest;
import com.xmu.iginx.assoc.modules.data.dto.DataImportRequest;
import com.xmu.iginx.assoc.modules.data.dto.StructuredQueryRequest;
import com.xmu.iginx.assoc.modules.data.dto.StructuredRowCreateRequest;
import com.xmu.iginx.assoc.modules.data.dto.StructuredRowDeleteRequest;
import com.xmu.iginx.assoc.modules.data.dto.StructuredRowUpdateRequest;
import com.xmu.iginx.assoc.modules.data.dto.TimeSeriesQueryRequest;
import com.xmu.iginx.assoc.modules.data.service.DataExportService;
import com.xmu.iginx.assoc.modules.data.service.DataImportService;
import com.xmu.iginx.assoc.modules.data.service.DataMaintainService;
import com.xmu.iginx.assoc.modules.data.service.DataQueryService;
import com.xmu.iginx.assoc.modules.data.service.DataResourceTreeService;
import com.xmu.iginx.assoc.modules.data.util.DataFileStorageService;
import com.xmu.iginx.assoc.modules.data.vo.DataExportResultVO;
import com.xmu.iginx.assoc.modules.data.vo.DataImportResultVO;
import com.xmu.iginx.assoc.modules.data.vo.DataResourceTreeNodeVO;
import com.xmu.iginx.assoc.modules.data.vo.StructuredQueryResultVO;
import com.xmu.iginx.assoc.modules.data.vo.StructuredSchemaVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 数据资源控制器。
 *
 * <p>说明：</p>
 * <p>1. 导入接口统一为一个入口，不再由控制层区分“时序/结构化”；</p>
 * <p>2. 导入语义由 targetPath 前缀决定（ts.* 或 rt.*）；</p>
 * <p>3. 结构化查询拆分为“查表结构”和“查表数据”两个接口。</p>
 */
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
    private final DataFileStorageService fileStorageService;
    private final DataResourceTreeService dataResourceTreeService;

    /**
     * 统一导入接口。
     */
    @Operation(summary = "Import Data")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DataImportResultVO> importData(@Valid @RequestPart("request") DataImportRequest request,
                                                 @RequestPart("file") MultipartFile file) {
        return Result.success(dataImportService.importData(request, file));
    }

    /**
     * 发起导出任务。
     */
    @Operation(summary = "Export Data")
    @PostMapping("/export")
    public Result<DataExportResultVO> exportData(@Valid @RequestBody DataExportRequest request) {
        return Result.success(dataExportService.exportData(request));
    }

    /**
     * 查询导出任务结果。
     */
    @Operation(summary = "Query Export Task")
    @GetMapping("/export/tasks/{taskId}")
    public Result<DataExportResultVO> exportTask(@PathVariable Long taskId) {
        return Result.success(dataExportService.queryExportTask(taskId));
    }

    /**
     * 查询时序数据。
     */
    @Operation(summary = "Query Time Series Data")
    @PostMapping("/query/ts")
    public Result<TimeSeriesQueryResultVO> queryTimeSeries(@Valid @RequestBody TimeSeriesQueryRequest request) {
        return Result.success(dataQueryService.queryTimeSeries(request));
    }

    /**
     * 查询结构化表结构（仅列定义）。
     *
     * <p>内部使用 SHOW COLUMNS rt.xxx.* 语法，遵循 IGinX 用户手册。</p>
     */
    @Operation(summary = "Query Structured Schema")
    @GetMapping("/query/struct/schema")
    public Result<StructuredSchemaVO> queryStructuredSchema(@RequestParam String tablePath) {
        return Result.success(dataQueryService.queryStructuredSchema(tablePath));
    }

    /**
     * 查询结构化表数据。
     */
    @Operation(summary = "Query Structured Data")
    @PostMapping("/query/struct")
    public Result<StructuredQueryResultVO> queryStructured(@Valid @RequestBody StructuredQueryRequest request) {
        return Result.success(dataQueryService.queryStructured(request));
    }

    /**
     * 查询资源树。
     */
    @Operation(summary = "Resource Tree")
    @GetMapping("/resources/tree")
    public Result<List<DataResourceTreeNodeVO>> resourceTree() {
        return Result.success(dataResourceTreeService.buildTree());
    }

    /**
     * 删除路径数据（可选包含子路径）。
     */
    @Operation(summary = "Delete Columns Data")
    @PostMapping("/columns/delete")
    public Result<Void> deleteColumns(@Valid @RequestBody DataColumnsDeleteRequest request) {
        dataMaintainService.deleteColumns(request);
        return Result.success();
    }

    /**
     * 新增一行 rt 语义数据。
     */
    @Operation(summary = "Create Structured Row")
    @PostMapping("/struct/rows")
    public Result<Void> createStructuredRow(@Valid @RequestBody StructuredRowCreateRequest request) {
        dataMaintainService.createStructuredRow(request);
        return Result.success();
    }

    /**
     * 更新一行 rt 语义数据。
     */
    @Operation(summary = "Update Structured Row")
    @PutMapping("/struct/rows")
    public Result<Void> updateStructuredRow(@Valid @RequestBody StructuredRowUpdateRequest request) {
        dataMaintainService.updateStructuredRow(request);
        return Result.success();
    }

    /**
     * 删除一行 rt 语义数据。
     */
    @Operation(summary = "Delete Structured Row")
    @DeleteMapping("/struct/rows")
    public Result<Void> deleteStructuredRow(@Valid @RequestBody StructuredRowDeleteRequest request) {
        dataMaintainService.deleteStructuredRow(request);
        return Result.success();
    }

    /**
     * 下载导入/导出错误文件。
     *
     * <p>这里会做两层安全校验：</p>
     * <p>1. 拒绝 .. 目录穿越；</p>
     * <p>2. 校验解析后的真实路径必须位于文件根目录下。</p>
     */
    @Operation(summary = "Download Export File")
    @GetMapping("/files/{fileName}")
    public void downloadFile(@PathVariable String fileName, HttpServletResponse response) {
        if (fileName == null || fileName.isBlank() || fileName.contains("..")) {
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
