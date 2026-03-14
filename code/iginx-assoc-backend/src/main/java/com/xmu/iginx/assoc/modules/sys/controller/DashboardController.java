package com.xmu.iginx.assoc.modules.sys.controller;

import com.xmu.iginx.assoc.common.Result;
import com.xmu.iginx.assoc.modules.sys.service.DashboardService;
import com.xmu.iginx.assoc.modules.sys.vo.DashboardSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统仪表盘接口。
 */
@Tag(name = "Dashboard")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 获取系统总览统计数据。
     *
     * @return 总览数据
     */
    @Operation(summary = "系统总览")
    @GetMapping("/summary")
    public Result<DashboardSummaryVO> summary() {
        return Result.success(dashboardService.fetchSummary());
    }
}
