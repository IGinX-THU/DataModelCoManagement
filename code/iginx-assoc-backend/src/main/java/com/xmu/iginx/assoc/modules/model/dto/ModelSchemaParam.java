package com.xmu.iginx.assoc.modules.model.dto;

import lombok.Data;

@Data
public class ModelSchemaParam {

    private String name;
    private String type;
    private String unit;
    private String description;
    private Boolean required;
}
