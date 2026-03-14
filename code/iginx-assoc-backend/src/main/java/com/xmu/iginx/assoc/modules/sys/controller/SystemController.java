package com.xmu.iginx.assoc.modules.sys.controller;

import com.xmu.iginx.assoc.common.Result;
import com.xmu.iginx.assoc.modules.sys.dto.SqlExecuteRequest;
import com.xmu.iginx.assoc.modules.sys.service.SystemLogService;
import com.xmu.iginx.assoc.modules.sys.service.SystemSqlService;
import com.xmu.iginx.assoc.modules.sys.vo.SqlExecuteResultVO;
import com.xmu.iginx.assoc.modules.sys.vo.SystemLogEntryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统管理接口，提供健康检查、日志与 SQL 执行能力。
 */
@Tag(name = "System Management")
@RestController
@RequestMapping("/api/v1/sys")
@RequiredArgsConstructor
public class SystemController {

    private final SystemLogService systemLogService;
    private final SystemSqlService systemSqlService;

    /**
     * 健康检查接口。
     *
     * @return 服务状态文本
     */
    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("IGinX Association System is running.");
    }

    /**
     * 查询系统日志。
     *
     * @param limit 返回数量上限
     * @param level 日志级别过滤
     * @param keyword 关键字过滤
     * @return 日志列表
     */
    @Operation(summary = "系统日志")
    @GetMapping("/logs")
    public Result<List<SystemLogEntryVO>> logs(@RequestParam(value = "limit", required = false) Integer limit,
                                               @RequestParam(value = "level", required = false) String level,
                                               @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(systemLogService.listLogs(limit, level, keyword));
    }

    /**
     * 执行 SQL 语句。
     *
     * @param request SQL 请求
     * @return 执行结果
     */
    @Operation(summary = "SQL 控制台")
    @PostMapping("/sql")
    public Result<SqlExecuteResultVO> executeSql(@RequestBody SqlExecuteRequest request) {
        return Result.success(systemSqlService.execute(request));
    }
}
