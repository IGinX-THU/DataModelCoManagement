package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class TimeSeriesImportRequest {

    @NotNull(message = "数据源不能为空")
    private Long sourceId;

    @NotBlank(message = "存储组路径不能为空")
    private String storageGroup;

    @NotBlank(message = "时间戳列不能为空")
    private String timestampColumn;

    private String timestampFormat;

    @Valid
    private List<TimeSeriesColumnMappingDTO> mappings;
}
