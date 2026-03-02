package com.xmu.iginx.assoc.modules.sys.service;

import com.xmu.iginx.assoc.modules.sys.dto.SqlExecuteRequest;
import com.xmu.iginx.assoc.modules.sys.vo.SqlExecuteResultVO;

public interface SystemSqlService {

    SqlExecuteResultVO execute(SqlExecuteRequest request);
}
