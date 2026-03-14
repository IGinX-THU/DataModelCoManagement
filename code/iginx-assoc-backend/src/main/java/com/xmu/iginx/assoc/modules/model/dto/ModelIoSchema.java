package com.xmu.iginx.assoc.modules.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 模型输入输出结构定义。
 */
@Data
public class ModelIoSchema {

    private List<ModelSchemaParam> inputs;
    private List<ModelSchemaParam> outputs;
    private List<String> dependencies;
}
