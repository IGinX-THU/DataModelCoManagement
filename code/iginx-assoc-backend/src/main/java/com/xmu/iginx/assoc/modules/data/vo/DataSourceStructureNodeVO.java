package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据源结构树节点视图对象。
 */
@Data
public class DataSourceStructureNodeVO {

    private String id;

    private String name;

    private String type;

    private List<DataSourceStructureNodeVO> children = new ArrayList<>();
}
