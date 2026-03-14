package com.xmu.iginx.assoc.modules.external.service;

import com.xmu.iginx.assoc.modules.external.dto.ExternalAlgorithmJobRequest;
import com.xmu.iginx.assoc.modules.external.dto.ExternalDataExportJobRequest;
import com.xmu.iginx.assoc.modules.external.dto.ExternalDataImportJobRequest;
import com.xmu.iginx.assoc.modules.external.dto.ExternalModelJobRequest;
import com.xmu.iginx.assoc.modules.external.vo.ExternalJobCreateResponse;
import com.xmu.iginx.assoc.modules.external.vo.ExternalJobResultResponse;
import com.xmu.iginx.assoc.modules.external.vo.ExternalJobStatusResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 外部任务服务接口。
 */
public interface ExternalJobService {

    /**
     * 提交模型调用任务。
     *
     * @param request 请求体
     * @param traceId 链路追踪 ID
     * @return 创建结果
     */
    ExternalJobCreateResponse submitModelJob(ExternalModelJobRequest request, String traceId);

    /**
     * 提交算法调用任务。
     *
     * @param request 请求体
     * @param traceId 链路追踪 ID
     * @return 创建结果
     */
    ExternalJobCreateResponse submitAlgorithmJob(ExternalAlgorithmJobRequest request, String traceId);

    /**
     * 提交数据导入任务。
     *
     * @param request 请求体
     * @param file 上传文件
     * @param traceId 链路追踪 ID
     * @return 创建结果
     */
    ExternalJobCreateResponse submitDataImportJob(ExternalDataImportJobRequest request, MultipartFile file, String traceId);

    /**
     * 提交数据导出任务。
     *
     * @param request 请求体
     * @param traceId 链路追踪 ID
     * @return 创建结果
     */
    ExternalJobCreateResponse submitDataExportJob(ExternalDataExportJobRequest request, String traceId);

    /**
     * 查询外部任务状态。
     *
     * @param jobId 任务 ID
     * @return 状态信息
     */
    ExternalJobStatusResponse getJobStatus(String jobId);

    /**
     * 查询外部任务结果。
     *
     * @param jobId 任务 ID
     * @return 结果信息
     */
    ExternalJobResultResponse getJobResult(String jobId);
}
