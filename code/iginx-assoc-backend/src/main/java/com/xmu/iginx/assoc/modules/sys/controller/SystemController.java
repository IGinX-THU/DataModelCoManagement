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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "System Management")
@RestController
@RequestMapping("/api/v1/sys")
@RequiredArgsConstructor
public class SystemController {

    private final SystemLogService systemLogService;
    private final SystemSqlService systemSqlService;

    @Operation(summary = "Health Check")
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("IGinX Association System is running.");
    }

    @Operation(summary = "System Logs")
    @GetMapping("/logs")
    public Result<List<SystemLogEntryVO>> logs(@RequestParam(value = "limit", required = false) Integer limit,
                                               @RequestParam(value = "level", required = false) String level,
                                               @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(systemLogService.listLogs(limit, level, keyword));
    }

    @Operation(summary = "SQL Console")
    @PostMapping("/sql")
    public Result<SqlExecuteResultVO> executeSql(@RequestBody SqlExecuteRequest request) {
        return Result.success(systemSqlService.execute(request));
    }
}
