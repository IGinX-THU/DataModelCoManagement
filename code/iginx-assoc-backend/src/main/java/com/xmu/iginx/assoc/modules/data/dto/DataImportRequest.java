package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 统一数据导入请求。
 *
 * <p>约束说明：</p>
 * <p>1. 仅通过 IGinX 路径前缀识别语义（`ts.*` 或 `rt.*`）；</p>
 * <p>2. 不引入 schema/table 等传统关系语义到导入协议；</p>
 * <p>3. 前后端统一使用该请求模型。</p>
 */
@Data
public class DataImportRequest {

    /**
     * 导入目标路径，例如：`ts.factory.deviceA` 或 `rt.factory.lineA`。
     */
    @NotBlank(message = "导入目标路径不能为空")
    private String targetPath;

    /**
     * Excel 工作表索引（从 0 开始），CSV 场景下会被忽略。
     */
    private Integer sheetIndex = 0;

    /**
     * 时间戳列名（仅 `ts.*` 语义必填）。
     */
    private String timestampColumn;

    /**
     * 时间戳格式（可选），例如：`yyyy-MM-dd HH:mm:ss`。
     */
    private String timestampFormat;

    /**
     * 列映射（仅 `ts.*` 语义使用，可选）。
     */
    @Valid
    private List<TimeSeriesColumnMappingDTO> mappings;

    /**
     * 行键列名（仅 `rt.*` 语义使用，可选）。
     */
    private String keyColumn;
}
