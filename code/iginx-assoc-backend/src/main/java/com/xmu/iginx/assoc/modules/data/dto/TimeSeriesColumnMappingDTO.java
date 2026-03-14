package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 时序列映射 DTO。
 */
@Data
public class TimeSeriesColumnMappingDTO {

    @NotBlank(message = "列名不能为空")
    private String column;

    @NotBlank(message = "目标测点不能为空")
    private String target;

    private String dataType;
}
