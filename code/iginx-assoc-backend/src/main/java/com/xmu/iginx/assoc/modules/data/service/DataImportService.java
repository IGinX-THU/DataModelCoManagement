package com.xmu.iginx.assoc.modules.data.service;

import com.xmu.iginx.assoc.modules.data.dto.StructuredImportRequest;
import com.xmu.iginx.assoc.modules.data.dto.TimeSeriesImportRequest;
import com.xmu.iginx.assoc.modules.data.vo.DataImportResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface DataImportService {

    DataImportResultVO importTimeSeries(TimeSeriesImportRequest request, MultipartFile file);

    DataImportResultVO importStructured(StructuredImportRequest request, MultipartFile file);
}
