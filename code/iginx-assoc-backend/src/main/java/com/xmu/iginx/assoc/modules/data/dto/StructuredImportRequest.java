package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 结构化数据导入请求。
 */
@Data
public class StructuredImportRequest {

    /**
     * 导入目标路径（结构化前缀，通常为 rt.schema.table）。
     */
    @NotBlank(message = "导入目标路径不能为空")
    private String targetPath;

    /**
     * 是否自动建表（目标表不存在时）。
     */
    private boolean autoCreateTable = false;

    /**
     * 冲突策略：update（覆盖）/ignore（忽略已存在）。
     */
    private String conflictStrategy = "update";

    /**
     * 文件类型（CSV/EXCEL/JSON 等）。
     */
    private String fileType;

    /**
     * Excel 工作表索引（从 0 开始）。
     */
    private Integer sheetIndex = 0;

    /**
     * 主键列列表（用于去重或定位更新）。
     */
    private List<String> primaryKeys;
}
