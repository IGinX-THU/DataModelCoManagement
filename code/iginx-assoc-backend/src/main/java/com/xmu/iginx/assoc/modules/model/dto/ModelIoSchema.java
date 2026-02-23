package com.xmu.iginx.assoc.modules.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class ModelIoSchema {

    private List<ModelSchemaParam> inputs;
    private List<ModelSchemaParam> outputs;
    private List<String> dependencies;
}
