package com.xmu.iginx.assoc.modules.data.service;

import com.xmu.iginx.assoc.modules.data.dto.StructuredImportRequest;
import com.xmu.iginx.assoc.modules.data.dto.TimeSeriesImportRequest;
import com.xmu.iginx.assoc.modules.data.vo.DataImportResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据导入服务接口。
 */
public interface DataImportService {

    /**
     * 导入时序数据。
     *
     * @param request 导入请求
     * @param file 数据文件
     * @return 导入结果
     */
    DataImportResultVO importTimeSeries(TimeSeriesImportRequest request, MultipartFile file);

    /**
     * 导入结构化数据。
     *
     * @param request 导入请求
     * @param file 数据文件
     * @return 导入结果
     */
    DataImportResultVO importStructured(StructuredImportRequest request, MultipartFile file);
}
