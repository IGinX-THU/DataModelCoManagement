package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

/**
 * 存储引擎视图对象。
 */
@Data
public class StorageEngineVO {

    private String ip;

    private Integer port;

    private String type;

    private String schemaPrefix;

    private String dataPrefix;
}
