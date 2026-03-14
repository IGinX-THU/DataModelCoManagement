package com.xmu.iginx.assoc.modules.relation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 规则状态更新请求。
 */
@Data
public class RuleStatusRequest {

    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;
}
