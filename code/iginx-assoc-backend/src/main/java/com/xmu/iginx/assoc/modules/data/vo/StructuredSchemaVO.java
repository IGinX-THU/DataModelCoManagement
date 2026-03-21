package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

import java.util.List;

/**
 * 结构化表结构查询结果。
 */
@Data
public class StructuredSchemaVO {

    /**
     * 规范化后的表路径（例如 rt.user）。
     */
    private String tablePath;

    /**
     * 字段定义列表。
     */
    private List<StructuredSchemaColumnVO> columns;
}
