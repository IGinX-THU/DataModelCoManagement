package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 时序导入请求。
 */
@Data
public class TimeSeriesImportRequest {

    @NotBlank(message = "导入路径不能为空")
    private String storageGroup;

    @NotBlank(message = "时间戳列不能为空")
    private String timestampColumn;

    private String timestampFormat;

    @Valid
    private List<TimeSeriesColumnMappingDTO> mappings;
}
