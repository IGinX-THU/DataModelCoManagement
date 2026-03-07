package com.xmu.iginx.assoc.modules.external.service;

import com.xmu.iginx.assoc.modules.external.dto.ExternalAlgorithmJobRequest;
import com.xmu.iginx.assoc.modules.external.dto.ExternalDataExportJobRequest;
import com.xmu.iginx.assoc.modules.external.dto.ExternalDataImportJobRequest;
import com.xmu.iginx.assoc.modules.external.dto.ExternalModelJobRequest;
import com.xmu.iginx.assoc.modules.external.vo.ExternalJobCreateResponse;
import com.xmu.iginx.assoc.modules.external.vo.ExternalJobResultResponse;
import com.xmu.iginx.assoc.modules.external.vo.ExternalJobStatusResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ExternalJobService {

    ExternalJobCreateResponse submitModelJob(ExternalModelJobRequest request, String traceId);

    ExternalJobCreateResponse submitAlgorithmJob(ExternalAlgorithmJobRequest request, String traceId);

    ExternalJobCreateResponse submitDataImportJob(ExternalDataImportJobRequest request, MultipartFile file, String traceId);

    ExternalJobCreateResponse submitDataExportJob(ExternalDataExportJobRequest request, String traceId);

    ExternalJobStatusResponse getJobStatus(String jobId);

    ExternalJobResultResponse getJobResult(String jobId);
}
