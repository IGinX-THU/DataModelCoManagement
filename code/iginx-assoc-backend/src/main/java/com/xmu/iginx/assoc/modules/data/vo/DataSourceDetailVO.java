package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

import java.util.List;

/**
 * 数据源详情聚合视图对象。
 */
@Data
public class DataSourceDetailVO {

    private DataSourceVO meta;

    private List<StorageEngineVO> engines;

    private List<ColumnVO> columns;
}
