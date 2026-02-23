package com.xmu.iginx.assoc.modules.data.model;

import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;
import com.xmu.iginx.assoc.modules.data.entity.DataResourceEntity;
import com.xmu.iginx.assoc.modules.data.enums.DataSourceType;

public record DataSourceDetail(DataResourceEntity entity,
                               DataSourceType type,
                               DataSourceConnectionConfig config) {
}
