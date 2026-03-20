package com.xmu.iginx.assoc.modules.data.service;

import com.xmu.iginx.assoc.modules.data.dto.DataImportRequest;
import com.xmu.iginx.assoc.modules.data.vo.DataImportResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据导入服务接口。
 */
public interface DataImportService {

    /**
     * 按统一协议导入 CSV 数据。
     *
     * @param request 导入请求（目标路径 + KEY 方式）
     * @param file CSV 文件
     * @return 导入结果
     */
    DataImportResultVO importData(DataImportRequest request, MultipartFile file);
}
