package com.xmu.iginx.assoc.modules.data.controller;

import com.xmu.iginx.assoc.common.PageResult;
import com.xmu.iginx.assoc.common.Result;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceCreateRequest;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceQueryRequest;
import com.xmu.iginx.assoc.modules.data.service.DataSourceService;
import com.xmu.iginx.assoc.modules.data.service.StructureService;
import com.xmu.iginx.assoc.modules.data.vo.DataSourceDetailVO;
import com.xmu.iginx.assoc.modules.data.vo.DataSourceVO;
import com.xmu.iginx.assoc.modules.data.vo.TableColumnVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据源管理接口，提供数据源的增删改查与结构预览能力。
 */
@Tag(name = "Data Resource Management")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/data/sources", "/api/v1/datasources"})
public class DataSourceController {

    private final DataSourceService dataSourceService;
    private final StructureService structureService;

    /**
     * 新增数据源。
     *
     * @param request 数据源创建参数
     * @return 新增数据源 ID
     */
    @Operation(summary = "新增数据源")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody DataSourceCreateRequest request) {
        return Result.success(dataSourceService.createDataSource(request));
    }

    /**
     * 分页查询数据源列表。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @Operation(summary = "分页查询数据源")
    @GetMapping
    public Result<PageResult<DataSourceVO>> page(@Valid DataSourceQueryRequest request) {
        return Result.success(dataSourceService.pageDataSources(request));
    }

    /**
     * 查询数据源详情。
     *
     * @param id 数据源 ID
     * @return 数据源详情
     */
    @Operation(summary = "查询数据源详情")
    @GetMapping("/{id}")
    public Result<DataSourceVO> detail(@PathVariable Long id) {
        return Result.success(dataSourceService.getDataSource(id));
    }

    /**
     * 查询数据源详情（聚合）。
     *
     * @param id 数据源 ID
     * @param limit 兼容参数，当前不再返回路径列表
     * @return 数据源详情
     */
    @Operation(summary = "查询数据源详情(聚合)")
    @GetMapping("/{id}/detail")
    public Result<DataSourceDetailVO> detailAggregate(@PathVariable Long id,
                                                      @RequestParam(defaultValue = "200") int limit) {
        return Result.success(dataSourceService.getDetail(id, limit));
    }

    /**
     * 查询关系型表的字段列表。
     *
     * @param id 数据源 ID
     * @param schema Schema 名称
     * @param table 表名
     * @return 字段列表
     */
    @Operation(summary = "查询关系表字段列表")
    @GetMapping("/{id}/tables/{schema}/{table}/columns")
    public Result<List<TableColumnVO>> tableColumns(@PathVariable Long id,
                                                    @PathVariable String schema,
                                                    @PathVariable String table) {
        return Result.success(structureService.listTableColumns(id, schema, table));
    }

    /**
     * 测试数据源连接是否可用。
     *
     * @param request 连接测试参数
     * @return 操作结果
     */
    @Operation(summary = "测试数据源连接")
    @PostMapping("/test-connection")
    public Result<Void> testConnection(@Valid @RequestBody TestConnectionRequest request) {
        dataSourceService.testConnection(request.getSourceType(), request.getConnectionConfig());
        return Result.success();
    }

    /**
     * 数据源连接测试请求。
     */
    @Data
    public static class TestConnectionRequest {
        /**
         * 数据源类型。
         */
        @NotBlank(message = "数据源类型不能为空")
        private String sourceType;

        /**
         * 连接配置。
         */
        @Valid
        private DataSourceConnectionConfig connectionConfig;
    }
}
