package com.xmu.iginx.assoc.modules.sys.service;

import com.xmu.iginx.assoc.modules.sys.dto.SqlExecuteRequest;
import com.xmu.iginx.assoc.modules.sys.vo.SqlExecuteResultVO;

/**
 * 系统 SQL 服务接口。
 */
public interface SystemSqlService {

    /**
     * 执行 SQL 并返回结果。
     *
     * @param request SQL 请求
     * @return 执行结果
     */
    SqlExecuteResultVO execute(SqlExecuteRequest request);
}
