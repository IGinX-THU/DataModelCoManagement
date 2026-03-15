package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据资源树节点视图对象。
 */
@Data
public class DataResourceTreeNodeVO {

    /**
     * 节点唯一标识。
     */
    private String id;

    /**
     * 节点显示名称。
     */
    private String name;

    /**
     * 节点类型（source/schema/table/path 等）。
     */
    private String type;

    /**
     * 资源路径（时序路径或结构化路径）。
     */
    private String path;

    /**
     * 数据源 ID。
     */
    private Long sourceId;

    /**
     * Schema 名称（结构化资源使用）。
     */
    private String schema;

    /**
     * 表名（结构化资源使用）。
     */
    private String table;

    /**
     * 子节点列表。
     */
    private List<DataResourceTreeNodeVO> children = new ArrayList<>();
}
