package com.xmu.iginx.assoc.modules.model.vo;

import com.xmu.iginx.assoc.modules.model.dto.ModelSchemaParam;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型版本视图对象。
 */
@Data
public class ModelVersionVO {

    private Long id;
    private String version;
    private String fileType;
    private Long fileSize;
    private String fileMd5;
    private LocalDateTime uploadTime;
    private boolean latest;
    private List<ModelSchemaParam> inputs;
    private List<ModelSchemaParam> outputs;
    private List<ModelFunctionOptionVO> functions;
}
