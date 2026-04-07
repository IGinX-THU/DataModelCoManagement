package com.xmu.iginx.assoc.modules.taskchain.vo;

import lombok.Data;

import java.util.List;

/**
 * 任务链可选规则视图。
 */
@Data
public class TaskChainRuleOptionVO {

    private Long ruleId;
    private String ruleName;
    private String modelName;
    private String modelVersion;
    private String modelType;
    private String functionName;
    private String chainMode;
    private List<ParamVO> inputs;
    private List<ParamVO> outputs;

    /**
     * 参数视图。
     */
    @Data
    public static class ParamVO {

        private String name;
        private String type;
        private String defaultPath;
    }
}
