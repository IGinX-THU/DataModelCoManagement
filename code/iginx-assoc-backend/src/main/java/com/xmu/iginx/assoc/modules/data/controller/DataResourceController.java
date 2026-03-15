package com.xmu.iginx.assoc.modules.data.controller;

import com.xmu.iginx.assoc.common.Result;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.data.dto.DataColumnsDeleteRequest;
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
import com.xmu.iginx.assoc.modules.data.service.DataResourceTreeService;
import com.xmu.iginx.assoc.modules.data.service.StructureService;
import com.xmu.iginx.assoc.modules.data.util.DataFileStorageService;
import com.xmu.iginx.assoc.modules.data.vo.DataExportResultVO;
import com.xmu.iginx.assoc.modules.data.vo.DataImportResultVO;
import com.xmu.iginx.assoc.modules.data.vo.DataResourceTreeNodeVO;
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
import java.util.List;

/**
 * 数据资源相关接口，负责数据导入、导出、查询与结构维护。
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
    private final StructureService structureService;
    private final DataFileStorageService fileStorageService;
    private final DataResourceTreeService dataResourceTreeService;

    /**
     * 导入时序数据文件。
     *
     * @param request 导入参数
     * @param file 数据文件
     * @return 导入结果
     */
    @Operation(summary = "导入时序数据")
    @PostMapping(value = "/import/ts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DataImportResultVO> importTimeSeries(@Valid @RequestPart("request") TimeSeriesImportRequest request,
                                                       @RequestPart("file") MultipartFile file) {
        return Result.success(dataImportService.importTimeSeries(request, file));
    }

    /**
     * 导入结构化数据文件。
     *
     * @param request 导入参数
     * @param file 数据文件
     * @return 导入结果
     */
    @Operation(summary = "导入结构化数据")
    @PostMapping(value = "/import/struct", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DataImportResultVO> importStructured(@Valid @RequestPart("request") StructuredImportRequest request,
                                                       @RequestPart("file") MultipartFile file) {
        return Result.success(dataImportService.importStructured(request, file));
    }

    /**
     * 提交数据导出任务。
     *
     * @param request 导出请求
     * @return 导出任务结果
     */
    @Operation(summary = "导出数据")
    @PostMapping("/export")
    public Result<DataExportResultVO> exportData(@Valid @RequestBody DataExportRequest request) {
        return Result.success(dataExportService.exportData(request));
    }

    /**
     * 查询导出任务结果。
     *
     * @param taskId 导出任务 ID
     * @return 导出结果
     */
    @Operation(summary = "查询导出任务")
    @GetMapping("/export/tasks/{taskId}")
    public Result<DataExportResultVO> exportTask(@PathVariable Long taskId) {
        return Result.success(dataExportService.queryExportTask(taskId));
    }

    /**
     * 按条件查询时序数据。
     *
     * @param request 查询条件
     * @return 查询结果
     */
    @Operation(summary = "时序数据查询")
    @PostMapping("/query/ts")
    public Result<TimeSeriesQueryResultVO> queryTimeSeries(@Valid @RequestBody TimeSeriesQueryRequest request) {
        return Result.success(dataQueryService.queryTimeSeries(request));
    }

    /**
     * 按条件查询结构化数据。
     *
     * @param request 查询条件
     * @return 查询结果
     */
    @Operation(summary = "结构化数据查询")
    @PostMapping("/query/struct")
    public Result<StructuredQueryResultVO> queryStructured(@Valid @RequestBody StructuredQueryRequest request) {
        return Result.success(dataQueryService.queryStructured(request));
    }

    /**
     * 获取数据资源树（按前缀分类）。
     *
     * @return 资源树
     */
    @Operation(summary = "数据资源树")
    @GetMapping("/resources/tree")
    public Result<List<DataResourceTreeNodeVO>> resourceTree() {
        return Result.success(dataResourceTreeService.buildTree());
    }

    /**
     * 删除时序数据。
     *
     * @param request 删除条件
     * @return 操作结果
     */
    @Operation(summary = "时序数据维护")
    @PostMapping("/ts/delete")
    public Result<Void> deleteTimeSeries(@Valid @RequestBody TimeSeriesDeleteRequest request) {
        dataMaintainService.deleteTimeSeries(request);
        return Result.success();
    }

    /**
     * 删除路径及子路径数据（DELETE COLUMNS）。
     *
     * @param request 删除请求
     * @return 操作结果
     */
    @Operation(summary = "删除路径数据")
    @PostMapping("/columns/delete")
    public Result<Void> deleteColumns(@Valid @RequestBody DataColumnsDeleteRequest request) {
        dataMaintainService.deleteColumns(request);
        return Result.success();
    }

    /**
     * 新增结构化数据行。
     *
     * @param request 新增参数
     * @return 操作结果
     */
    @Operation(summary = "新增结构化数据")
    @PostMapping("/struct/rows")
    public Result<Void> createStructuredRow(@Valid @RequestBody StructuredRowCreateRequest request) {
        dataMaintainService.createStructuredRow(request);
        return Result.success();
    }

    /**
     * 更新结构化数据行。
     *
     * @param request 更新参数
     * @return 操作结果
     */
    @Operation(summary = "更新结构化数据")
    @PutMapping("/struct/rows")
    public Result<Void> updateStructuredRow(@Valid @RequestBody StructuredRowUpdateRequest request) {
        dataMaintainService.updateStructuredRow(request);
        return Result.success();
    }

    /**
     * 删除结构化数据行。
     *
     * @param request 删除参数
     * @return 操作结果
     */
    @Operation(summary = "删除结构化数据")
    @DeleteMapping("/struct/rows")
    public Result<Void> deleteStructuredRow(@Valid @RequestBody StructuredRowDeleteRequest request) {
        dataMaintainService.deleteStructuredRow(request);
        return Result.success();
    }

    /**
     * 删除存储组。
     *
     * @param request 删除参数
     * @return 操作结果
     */
    @Operation(summary = "删除存储组")
    @PostMapping("/structures/storage-groups/drop")
    public Result<Void> dropStorageGroup(@Valid @RequestBody StorageGroupRequest request) {
        structureService.dropStorageGroup(request);
        return Result.success();
    }

    /**
     * 创建测点。
     *
     * @param request 创建参数
     * @return 操作结果
     */
    @Operation(summary = "创建测点")
    @PostMapping("/structures/measurements")
    public Result<Void> createMeasurement(@Valid @RequestBody MeasurementRequest request) {
        structureService.createMeasurement(request);
        return Result.success();
    }

    /**
     * 删除测点。
     *
     * @param request 删除参数
     * @return 操作结果
     */
    @Operation(summary = "删除测点")
    @PostMapping("/structures/measurements/drop")
    public Result<Void> dropMeasurement(@Valid @RequestBody MeasurementRequest request) {
        structureService.dropMeasurement(request);
        return Result.success();
    }

    /**
     * 创建结构化表。
     *
     * @param request 创建参数
     * @return 操作结果
     */
    @Operation(summary = "创建表")
    @PostMapping("/structures/tables")
    public Result<Void> createTable(@Valid @RequestBody TableCreateRequest request) {
        structureService.createTable(request);
        return Result.success();
    }

    /**
     * 删除结构化表。
     *
     * @param request 删除参数
     * @return 操作结果
     */
    @Operation(summary = "删除表")
    @PostMapping("/structures/tables/drop")
    public Result<Void> dropTable(@Valid @RequestBody TableDropRequest request) {
        structureService.dropTable(request);
        return Result.success();
    }

    /**
     * 下载导出文件。
     *
     * @param fileName 文件名
     * @param response 响应对象
     */
    @Operation(summary = "下载导出文件")
    @GetMapping("/files/{fileName}")
    public void downloadFile(@PathVariable String fileName, HttpServletResponse response) {
        // 防止路径穿越攻击
        if (fileName.contains("..")) {
            throw BizException.badRequest("非法文件路径");
        }
        Path path = fileStorageService.resolveFile(fileName);
        // 强制校验文件在受控目录内
        if (!path.startsWith(fileStorageService.resolveRoot())) {
            throw BizException.badRequest("非法文件路径");
        }
        if (!Files.exists(path)) {
            throw BizException.badRequest("文件不存在");
        }
        try {
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
            // 通过 Content-Disposition 提示浏览器下载
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encoded + "\"");
            Files.copy(path, response.getOutputStream());
            response.flushBuffer();
        } catch (Exception ex) {
            // 下载失败统一转换为业务异常，避免泄露内部堆栈
            throw BizException.internal("文件下载失败: " + ex.getMessage());
        }
    }
}
