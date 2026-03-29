package com.xmu.iginx.assoc.modules.task.service.impl;

import com.xmu.iginx.assoc.modules.task.model.TaskExecutionPlan;

import java.util.LinkedHashMap;

/**
 * 模型执行器接口。
 * <p>
 * 不同模型类型（Python / MATLAB / C++）各自负责：
 * 1. 根据执行计划装配运行时文件；
 * 2. 调用指定函数；
 * 3. 将原始函数返回值转成 Java 可继续处理的对象。
 * </p>
 */
public interface TaskModelExecutor {

    /**
     * 是否支持指定模型类型。
     *
     * @param modelType 模型类型
     * @return 是否支持
     */
    boolean supports(String modelType);

    /**
     * 执行模型函数。
     *
     * @param plan 执行计划
     * @param arguments 已按参数名装配好的输入参数
     * @return 执行结果
     * @throws Exception 执行失败
     */
    ExecutionResult execute(TaskExecutionPlan plan,
                            LinkedHashMap<String, Object> arguments,
                            byte[] modelBytes) throws Exception;

    /**
     * 执行结果。
     */
    record ExecutionResult(Object rawResult, String runtimeLog) {
    }
}
