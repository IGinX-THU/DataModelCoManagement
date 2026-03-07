package com.xmu.iginx.assoc.modules.external.dto;

import com.xmu.iginx.assoc.modules.external.enums.ExternalJobType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExternalJobCreateRequest {

    @NotNull(message = "任务类型不能为空")
    private ExternalJobType jobType;

    @Valid
    private ExternalModelJobRequest modelJob;

    @Valid
    private ExternalAlgorithmJobRequest algorithmJob;

    @Valid
    private ExternalDataImportJobRequest dataImportJob;

    @Valid
    private ExternalDataExportJobRequest dataExportJob;

    private String stagedFilePath;

    private String stagedFileName;

    private String stagedContentType;
}
