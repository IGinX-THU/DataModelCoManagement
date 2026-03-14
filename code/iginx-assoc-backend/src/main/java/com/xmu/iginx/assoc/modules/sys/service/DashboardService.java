package com.xmu.iginx.assoc.modules.sys.service;

import com.xmu.iginx.assoc.modules.sys.vo.DashboardSummaryVO;

/**
 * 仪表盘服务接口。
 */
public interface DashboardService {

    /**
     * 获取仪表盘总览统计数据。
     *
     * @return 总览数据
     */
    DashboardSummaryVO fetchSummary();
}
