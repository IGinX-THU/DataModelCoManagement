package com.xmu.iginx.assoc.modules.model.dto;

import lombok.Data;

/**
 * 模型结构参数定义。
 */
@Data
public class ModelSchemaParam {

    private String name;
    private String type;
    private String unit;
    private String description;
    private Boolean required;
}
