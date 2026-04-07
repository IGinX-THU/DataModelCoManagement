package com.xmu.iginx.assoc.modules.taskchain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 任务链保存请求。
 */
@Data
public class TaskChainSaveRequest {

    @NotBlank(message = "任务链名称不能为空")
    @Size(max = 120, message = "任务链名称长度不能超过120个字符")
    private String chainName;

    @Valid
    @NotEmpty(message = "任务链节点不能为空")
    private List<NodeRequest> nodes;

    /**
     * 节点定义。
     */
    @Data
    public static class NodeRequest {

        @NotBlank(message = "节点ID不能为空")
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,31}$", message = "节点ID只能包含字母、数字和下划线，且必须以字母开头")
        private String nodeId;

        @Size(max = 120, message = "节点名称长度不能超过120个字符")
        private String nodeName;

        @NotNull(message = "节点规则ID不能为空")
        private Long ruleId;

        @Valid
        private Map<String, InputSourceRequest> inputs;
    }

    /**
     * 节点输入来源。
     */
    @Data
    public static class InputSourceRequest {

        @Size(max = 20, message = "输入来源类型长度不能超过20个字符")
        private String sourceType;

        @Size(max = 255, message = "输入路径长度不能超过255个字符")
        private String path;

        @Pattern(regexp = "^$|^[A-Za-z][A-Za-z0-9_]{0,31}$", message = "上游节点ID格式不合法")
        private String sourceNodeId;

        @Size(max = 120, message = "上游输出名称长度不能超过120个字符")
        private String sourceOutputName;
    }
}
