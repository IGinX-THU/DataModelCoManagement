package com.xmu.iginx.assoc.modules.relation.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 关联规则视图对象。
 */
@Data
public class AssociationRuleVO {

    private Long id;
    private String name;
    private Long modelId;
    private String modelName;
    private String modelVersion;
    private String modelType;
    private String functionName;
    private Map<String, String> bindings;
    private Map<String, String> results;
    private Boolean enabled;
    private LocalDateTime updateTime;
}
