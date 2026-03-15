package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

/**
 * 存储引擎视图对象。
 */
@Data
public class StorageEngineVO {

    /**
     * 存储引擎 IP 地址。
     */
    private String ip;

    /**
     * 存储引擎端口。
     */
    private Integer port;

    /**
     * 引擎类型（如 INFLUXDB/IOTDB/POSTGRESQL）。
     */
    private String type;

    /**
     * Schema 前缀（结构化数据）。
     */
    private String schemaPrefix;

    /**
     * 数据前缀（时序数据）。
     */
    private String dataPrefix;
}
