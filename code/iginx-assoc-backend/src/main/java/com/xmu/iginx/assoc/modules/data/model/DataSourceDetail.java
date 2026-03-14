package com.xmu.iginx.assoc.modules.data.model;

import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;
import com.xmu.iginx.assoc.modules.data.entity.DataResourceEntity;
import com.xmu.iginx.assoc.modules.data.enums.DataSourceType;

/**
 * 数据源详情记录。
 *
 * @param entity 数据源实体
 * @param type 数据源类型
 * @param config 连接配置
 */
public record DataSourceDetail(DataResourceEntity entity,
                               DataSourceType type,
                               DataSourceConnectionConfig config) {
}
