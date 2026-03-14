package com.xmu.iginx.assoc.modules.external.dto;

import com.xmu.iginx.assoc.modules.data.dto.DataExportRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 外部数据导出任务请求。
 */
@Data
public class ExternalDataExportJobRequest {

    @Valid
    @NotNull(message = "导出请求不能为空")
    private DataExportRequest exportRequest;
}
