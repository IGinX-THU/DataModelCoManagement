package com.xmu.iginx.assoc.modules.sys.service;

import com.xmu.iginx.assoc.modules.sys.vo.SystemLogEntryVO;

import java.util.List;

public interface SystemLogService {

    List<SystemLogEntryVO> listLogs(Integer limit, String level, String keyword);
}
