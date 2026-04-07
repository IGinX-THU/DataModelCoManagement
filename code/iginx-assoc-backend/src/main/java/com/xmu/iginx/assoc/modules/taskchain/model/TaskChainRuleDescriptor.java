package com.xmu.iginx.assoc.modules.taskchain.model;

import lombok.Data;

import java.util.List;

/**
 * 任务链可执行规则描述。
 */
@Data
public class TaskChainRuleDescriptor {

    private Long ruleId;
    private String ruleName;
    private Boolean enabled;
    private Long modelId;
    private String modelName;
    private String modelVersion;
    private String modelType;
    private String modelFileName;
    private String modelStoragePath;
    private Long modelFileSize;
    private String functionName;
    private String chainMode;
    private List<ParamDescriptor> inputs;
    private List<ParamDescriptor> outputs;

    /**
     * 参数描述。
     */
    @Data
    public static class ParamDescriptor {

        private String name;
        private String type;
        private String defaultPath;
        private String pathKind;
    }
}
