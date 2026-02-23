package com.xmu.iginx.assoc.modules.data.service;

import com.xmu.iginx.assoc.modules.data.dto.StructuredQueryRequest;
import com.xmu.iginx.assoc.modules.data.dto.TimeSeriesQueryRequest;
import com.xmu.iginx.assoc.modules.data.vo.StructuredQueryResultVO;
import com.xmu.iginx.assoc.modules.data.vo.TimeSeriesQueryResultVO;

public interface DataQueryService {

    TimeSeriesQueryResultVO queryTimeSeries(TimeSeriesQueryRequest request);

    StructuredQueryResultVO queryStructured(StructuredQueryRequest request);
}
