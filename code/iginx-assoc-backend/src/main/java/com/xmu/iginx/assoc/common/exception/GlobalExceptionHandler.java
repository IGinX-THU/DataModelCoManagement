package com.xmu.iginx.assoc.common.exception;

import com.xmu.iginx.assoc.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 全局异常处理器，统一将异常转换为标准响应。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理自定义业务异常。
     *
     * @param e 业务异常
     * @param request 当前请求
     * @return 统一错误响应
     */
    @ExceptionHandler(BizException.class)
    public Result<?> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("Request ID: {}, BizException: {}", request.getRequestId(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理方法参数校验失败异常。
     *
     * @param e 参数校验异常
     * @param request 当前请求
     * @return 统一错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        // 优先返回字段级校验信息，避免向前端返回空消息
        String message = fieldError == null ? "请求参数校验失败" : fieldError.getDefaultMessage();
        log.warn("Request ID: {}, ValidationException: {}", request.getRequestId(), message);
        return Result.error(400, message);
    }

    /**
     * 处理绑定异常（如表单/查询参数绑定失败）。
     *
     * @param e 绑定异常
     * @param request 当前请求
     * @return 统一错误响应
     */
    @ExceptionHandler(BindException.class)
    public Result<?> handleBindException(BindException e, HttpServletRequest request) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        // 无字段错误时给出通用提示，保证返回可读信息
        String message = fieldError == null ? "请求参数绑定失败" : fieldError.getDefaultMessage();
        log.warn("Request ID: {}, BindException: {}", request.getRequestId(), message);
        return Result.error(400, message);
    }

    /**
     * 兜底处理未知异常。
     *
     * @param e 异常
     * @param request 当前请求
     * @return 统一错误响应
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e, HttpServletRequest request) {
        log.error("Request ID: {}, Exception: ", request.getRequestId(), e);
        return Result.error(500, "系统内部错误");
    }

    /**
     * 处理静态资源不存在异常。
     *
     * @param e 资源异常
     * @param request 当前请求
     * @return 统一错误响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<?> handleNoResourceFound(NoResourceFoundException e, HttpServletRequest request) {
        log.warn("Request ID: {}, NoResourceFound: {}", request.getRequestId(), e.getResourcePath());
        return Result.error(404, "资源不存在");
    }
}
