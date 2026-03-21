package com.xmu.iginx.assoc.modules.data.service;

import com.xmu.iginx.assoc.modules.data.dto.StructuredQueryRequest;
import com.xmu.iginx.assoc.modules.data.dto.TimeSeriesQueryRequest;
import com.xmu.iginx.assoc.modules.data.vo.StructuredQueryResultVO;
import com.xmu.iginx.assoc.modules.data.vo.StructuredSchemaVO;
import com.xmu.iginx.assoc.modules.data.vo.TimeSeriesQueryResultVO;

/**
 * 数据查询服务接口。
 */
public interface DataQueryService {

    /**
     * 查询时序数据。
     *
     * @param request 查询请求
     * @return 时序查询结果
     */
    TimeSeriesQueryResultVO queryTimeSeries(TimeSeriesQueryRequest request);

    /**
     * 查询结构化表结构（列名与类型）。
     *
     * @param tablePath 结构化表路径（例如 rt.user）
     * @return 表结构信息
     */
    StructuredSchemaVO queryStructuredSchema(String tablePath);

    /**
     * 查询结构化数据。
     *
     * @param request 查询请求
     * @return 结构化查询结果
     */
    StructuredQueryResultVO queryStructured(StructuredQueryRequest request);
}
