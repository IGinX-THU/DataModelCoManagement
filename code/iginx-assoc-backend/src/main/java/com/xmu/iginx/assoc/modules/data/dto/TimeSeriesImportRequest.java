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

    /**
     * 导入目标存储组路径（如 ts.root.device）。
     */
    @NotBlank(message = "导入路径不能为空")
    private String storageGroup;

    /**
     * 时间戳列名。
     */
    @NotBlank(message = "时间戳列不能为空")
    private String timestampColumn;

    /**
     * 时间戳格式（例如 yyyy-MM-dd HH:mm:ss），为空时使用默认解析策略。
     */
    private String timestampFormat;

    /**
     * 列映射列表（源列 -> 目标测点）。
     */
    @Valid
    private List<TimeSeriesColumnMappingDTO> mappings;
}
