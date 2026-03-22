package com.xmu.iginx.assoc.modules.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 模型档案更新请求。
 */
@Data
public class ModelProfileUpdateRequest {

    /**
     * 需要更新结构定义的模型版本 ID（可选）。
     * 为空时默认更新当前最新版本。
     */
    private Long assetId;

    @NotBlank(message = "模型名称不能为空")
    private String name;

    private String description;

    private String developer;

    private String usageScope;

    private String ioSchema;
}
