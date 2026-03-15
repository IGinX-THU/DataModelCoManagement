package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 时序列映射 DTO。
 */
@Data
public class TimeSeriesColumnMappingDTO {

    /**
     * 源数据列名（CSV/Excel 中的列）。
     */
    @NotBlank(message = "列名不能为空")
    private String column;

    /**
     * 目标测点路径。
     */
    @NotBlank(message = "目标测点不能为空")
    private String target;

    /**
     * 数据类型（可选，缺省时自动推断或使用默认）。
     */
    private String dataType;
}
