package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 存储组操作请求。
 */
@Data
public class StorageGroupRequest {

    @NotNull(message = "数据源不能为空")
    private Long sourceId;

    @NotBlank(message = "存储组路径不能为空")
    private String path;
}
