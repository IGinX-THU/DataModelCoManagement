package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据资源树节点视图对象。
 */
@Data
public class DataResourceTreeNodeVO {

    private String id;

    private String name;

    private String type;

    private String path;

    private Long sourceId;

    private String schema;

    private String table;

    private String mountPath;

    private List<DataResourceTreeNodeVO> children = new ArrayList<>();
}
