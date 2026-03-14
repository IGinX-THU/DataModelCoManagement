package com.xmu.iginx.assoc.modules.sys.service;

import com.xmu.iginx.assoc.modules.sys.vo.SystemLogEntryVO;

import java.util.List;

/**
 * 系统日志服务接口。
 */
public interface SystemLogService {

    /**
     * 查询系统日志。
     *
     * @param limit 返回数量上限
     * @param level 日志级别过滤
     * @param keyword 关键字过滤
     * @return 日志列表
     */
    List<SystemLogEntryVO> listLogs(Integer limit, String level, String keyword);
}
