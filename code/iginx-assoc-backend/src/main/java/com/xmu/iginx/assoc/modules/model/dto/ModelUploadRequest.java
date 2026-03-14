package com.xmu.iginx.assoc.modules.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 模型上传请求。
 */
@Data
public class ModelUploadRequest {

    private Long profileId;

    private String name;

    private String description;

    private String developer;

    private String usageScope;

    @NotBlank(message = "模型版本不能为空")
    private String version;

    @NotBlank(message = "模型类型不能为空")
    private String type;

    private String ioSchema;
}
