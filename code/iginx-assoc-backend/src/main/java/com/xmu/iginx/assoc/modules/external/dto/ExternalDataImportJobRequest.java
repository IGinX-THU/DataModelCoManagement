package com.xmu.iginx.assoc.modules.external.dto;

import com.xmu.iginx.assoc.modules.data.dto.StructuredImportRequest;
import com.xmu.iginx.assoc.modules.data.dto.TimeSeriesImportRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExternalDataImportJobRequest {

    @NotBlank(message = "导入类型不能为空")
    private String importType;

    @Valid
    private TimeSeriesImportRequest timeSeriesRequest;

    @Valid
    private StructuredImportRequest structuredRequest;
}
