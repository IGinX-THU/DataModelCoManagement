package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 删除路径数据请求。
 */
@Data
public class DataColumnsDeleteRequest {

    @NotBlank(message = "路径不能为空")
    private String path;

    /**
     * 是否同时删除子路径数据。
     */
    private Boolean includeChildren;
}
