package com.xmu.iginx.assoc.modules.data.service;

import com.xmu.iginx.assoc.modules.data.dto.StructuredRowCreateRequest;
import com.xmu.iginx.assoc.modules.data.dto.StructuredRowDeleteRequest;
import com.xmu.iginx.assoc.modules.data.dto.StructuredRowUpdateRequest;
import com.xmu.iginx.assoc.modules.data.dto.DataColumnsDeleteRequest;
import com.xmu.iginx.assoc.modules.data.dto.TimeSeriesDeleteRequest;

/**
 * 数据维护服务接口。
 */
public interface DataMaintainService {

    /**
     * 删除时序数据。
     *
     * @param request 删除请求
     */
    void deleteTimeSeries(TimeSeriesDeleteRequest request);

    /**
     * 新增结构化数据行。
     *
     * @param request 新增请求
     */
    void createStructuredRow(StructuredRowCreateRequest request);

    /**
     * 更新结构化数据行。
     *
     * @param request 更新请求
     */
    void updateStructuredRow(StructuredRowUpdateRequest request);

    /**
     * 删除结构化数据行。
     *
     * @param request 删除请求
     */
    void deleteStructuredRow(StructuredRowDeleteRequest request);

    /**
     * 删除路径下的全部数据（DELETE COLUMNS）。
     *
     * @param request 删除请求
     */
    void deleteColumns(DataColumnsDeleteRequest request);
}
