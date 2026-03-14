package com.xmu.iginx.assoc.modules.relation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 关联规则更新请求。
 */
@Data
public class AssociationRuleUpdateRequest {

    @NotBlank(message = "规则名称不能为空")
    private String name;

    private String triggerType;

    private String cronExp;

    @NotNull(message = "输入绑定不能为空")
    private Map<String, String> bindings;

    @NotNull(message = "输出绑定不能为空")
    private Map<String, String> results;
}
