package com.xmu.iginx.assoc.modules.model.vo;

import com.xmu.iginx.assoc.modules.model.dto.ModelSchemaParam;
import lombok.Data;

import java.util.List;

/**
 * 模型结构解析结果视图对象。
 */
@Data
public class ModelSchemaParseVO {

    private List<ModelSchemaParam> inputs;
    private List<ModelSchemaParam> outputs;
    private String parseMode;
    private String message;
}
