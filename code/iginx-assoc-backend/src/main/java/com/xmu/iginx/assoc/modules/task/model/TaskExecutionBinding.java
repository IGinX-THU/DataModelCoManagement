package com.xmu.iginx.assoc.modules.task.model;

import lombok.Data;

/**
 * 任务执行绑定项。
 * <p>
 * 用于记录某个输入/输出参数在本次任务中的真实执行信息，
 * 包括参数名、类型、方向、配置路径与最终落地路径。
 * </p>
 */
@Data
public class TaskExecutionBinding {

    /**
     * 参数名称。
     */
    private String name;

    /**
     * 参数类型，例如 FLOAT / INT / BOOLEAN / STRING / ARRAY / OBJECT。
     */
    private String type;

    /**
     * 输入输出方向：INPUT / OUTPUT。
     */
    private String direction;

    /**
     * 原始配置路径。
     * <p>
     * 输入参数表示绑定到的数据路径；输出参数表示用户在规则中填写的路径，留空则为空字符串。
     * </p>
     */
    private String configuredPath;

    /**
     * 本次任务实际使用的路径。
     * <p>
     * 输入参数通常与 configuredPath 相同；
     * 输出参数在 configuredPath 为空时，会解析为默认路径 task.result.<taskId>.<outputName>。
     * </p>
     */
    private String resolvedPath;

    /**
     * 路径类别：TS / RT / CUSTOM / TASK_RESULT。
     */
    private String pathKind;
}
