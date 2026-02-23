package com.xmu.iginx.assoc.modules.data.controller;

import com.xmu.iginx.assoc.common.PageResult;
import com.xmu.iginx.assoc.common.Result;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceCreateRequest;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceQueryRequest;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceUpdateRequest;
import com.xmu.iginx.assoc.modules.data.service.DataSourceService;
import com.xmu.iginx.assoc.modules.data.service.StructureService;
import com.xmu.iginx.assoc.modules.data.vo.DataSourceStructureNodeVO;
import com.xmu.iginx.assoc.modules.data.vo.DataSourceVO;
import com.xmu.iginx.assoc.modules.data.vo.TableColumnVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Data Resource Management")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/data/sources", "/api/v1/datasources"})
public class DataSourceController {

    private final DataSourceService dataSourceService;
    private final StructureService structureService;

    @Operation(summary = "\u65b0\u589e\u6570\u636e\u6e90")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody DataSourceCreateRequest request) {
        return Result.success(dataSourceService.createDataSource(request));
    }

    @Operation(summary = "\u5206\u9875\u67e5\u8be2\u6570\u636e\u6e90")
    @GetMapping
    public Result<PageResult<DataSourceVO>> page(@Valid DataSourceQueryRequest request) {
        return Result.success(dataSourceService.pageDataSources(request));
    }

    @Operation(summary = "\u67e5\u8be2\u6570\u636e\u6e90\u8be6\u60c5")
    @GetMapping("/{id}")
    public Result<DataSourceVO> detail(@PathVariable Long id) {
        return Result.success(dataSourceService.getDataSource(id));
    }

    @Operation(summary = "\u67e5\u8be2\u6570\u636e\u6e90\u7ed3\u6784\u9884\u89c8")
    @GetMapping("/{id}/structure")
    public Result<List<DataSourceStructureNodeVO>> structure(@PathVariable Long id) {
        return Result.success(dataSourceService.listStructure(id));
    }

    @Operation(summary = "\u67e5\u8be2\u5173\u7cfb\u8868\u5b57\u6bb5\u5217\u8868")
    @GetMapping("/{id}/tables/{schema}/{table}/columns")
    public Result<List<TableColumnVO>> tableColumns(@PathVariable Long id,
                                                    @PathVariable String schema,
                                                    @PathVariable String table) {
        return Result.success(structureService.listTableColumns(id, schema, table));
    }

    @Operation(summary = "\u66f4\u65b0\u6570\u636e\u6e90")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DataSourceUpdateRequest request) {
        dataSourceService.updateDataSource(id, request);
        return Result.success();
    }

    @Operation(summary = "\u5220\u9664\u6570\u636e\u6e90")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id,
                               @RequestParam(defaultValue = "false") boolean force) {
        dataSourceService.removeDataSource(id, force);
        return Result.success();
    }

    @Operation(summary = "\u6d4b\u8bd5\u6570\u636e\u6e90\u8fde\u63a5")
    @PostMapping("/test-connection")
    public Result<Void> testConnection(@Valid @RequestBody TestConnectionRequest request) {
        dataSourceService.testConnection(request.getSourceType(), request.getConnectionConfig());
        return Result.success();
    }

    @Data
    public static class TestConnectionRequest {
        @NotBlank(message = "\u6570\u636e\u6e90\u7c7b\u578b\u4e0d\u80fd\u4e3a\u7a7a")
        private String sourceType;

        @Valid
        private DataSourceConnectionConfig connectionConfig;
    }
}
