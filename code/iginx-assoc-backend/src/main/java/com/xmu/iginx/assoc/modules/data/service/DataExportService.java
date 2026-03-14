package com.xmu.iginx.assoc.modules.data.service;

import com.xmu.iginx.assoc.modules.data.dto.DataExportRequest;
import com.xmu.iginx.assoc.modules.data.vo.DataExportResultVO;

/**
 * 数据导出服务接口。
 */
public interface DataExportService {

    /**
     * 发起数据导出任务（同步或异步）。
     *
     * @param request 导出请求
     * @return 导出结果
     */
    DataExportResultVO exportData(DataExportRequest request);

    /**
     * 查询导出任务结果。
     *
     * @param taskId 任务 ID
     * @return 导出结果
     */
    DataExportResultVO queryExportTask(Long taskId);
}
