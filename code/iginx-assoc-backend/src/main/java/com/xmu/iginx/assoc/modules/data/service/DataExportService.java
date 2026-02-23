package com.xmu.iginx.assoc.modules.data.service;

import com.xmu.iginx.assoc.modules.data.dto.DataExportRequest;
import com.xmu.iginx.assoc.modules.data.vo.DataExportResultVO;

public interface DataExportService {

    DataExportResultVO exportData(DataExportRequest request);

    DataExportResultVO queryExportTask(Long taskId);
}
