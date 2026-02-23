package com.xmu.iginx.assoc.modules.data.service;

import com.xmu.iginx.assoc.modules.data.dto.StructuredRowCreateRequest;
import com.xmu.iginx.assoc.modules.data.dto.StructuredRowDeleteRequest;
import com.xmu.iginx.assoc.modules.data.dto.StructuredRowUpdateRequest;
import com.xmu.iginx.assoc.modules.data.dto.TimeSeriesDeleteRequest;

public interface DataMaintainService {

    void deleteTimeSeries(TimeSeriesDeleteRequest request);

    void createStructuredRow(StructuredRowCreateRequest request);

    void updateStructuredRow(StructuredRowUpdateRequest request);

    void deleteStructuredRow(StructuredRowDeleteRequest request);
}
