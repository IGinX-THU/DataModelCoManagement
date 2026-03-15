package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

import java.util.List;

/**
 * 数据源详情聚合视图对象。
 */
@Data
public class DataSourceDetailVO {

    /**
     * 数据源基础信息。
     */
    private DataSourceVO meta;

    /**
     * 关联的存储引擎列表。
     */
    private List<StorageEngineVO> engines;
}
