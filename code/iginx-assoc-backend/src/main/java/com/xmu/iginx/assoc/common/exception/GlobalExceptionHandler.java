package com.xmu.iginx.assoc.common.exception;

import com.xmu.iginx.assoc.common.Result;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

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
        if (e.getCode() != null && e.getCode() >= 500) {
            log.error("Request ID: {}, BizException: {}", request.getRequestId(), buildRequestSummary(request), e);
        } else {
            log.warn("Request ID: {}, BizException: {}, message={}",
                request.getRequestId(), buildRequestSummary(request), e.getMessage());
        }
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
        String message = fieldError == null
            ? "请求参数校验失败，请检查提交内容"
            : "字段[" + fieldError.getField() + "]校验失败：" + fieldError.getDefaultMessage();
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
        String message = fieldError == null
            ? "请求参数绑定失败，请检查参数格式"
            : "字段[" + fieldError.getField() + "]绑定失败：" + fieldError.getDefaultMessage();
        log.warn("Request ID: {}, BindException: {}", request.getRequestId(), message);
        return Result.error(400, message);
    }

    /**
     * 处理请求方法不支持异常。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<?> handleMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        String supported = e.getSupportedHttpMethods() == null || e.getSupportedHttpMethods().isEmpty()
            ? "无"
            : e.getSupportedHttpMethods().stream().map(method -> method.name()).collect(Collectors.joining("、"));
        String message = "接口[" + request.getRequestURI() + "]不支持请求方法[" + e.getMethod() + "]，支持的方法有：" + supported;
        log.warn("Request ID: {}, MethodNotSupported: {}", request.getRequestId(), message);
        return Result.error(405, message);
    }

    /**
     * 处理请求体无法读取异常。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> handleMessageNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        Throwable cause = e.getMostSpecificCause();
        String message;
        if (cause instanceof UnrecognizedPropertyException ex) {
            message = "请求体包含未识别字段[" + ex.getPropertyName() + "]，请检查提交内容";
        } else if (cause instanceof InvalidFormatException ex) {
            String fieldPath = ex.getPath() == null || ex.getPath().isEmpty()
                ? "未知字段"
                : ex.getPath().stream().map(ref -> ref.getFieldName()).collect(Collectors.joining("."));
            String targetType = ex.getTargetType() == null ? "目标类型" : ex.getTargetType().getSimpleName();
            message = "字段[" + fieldPath + "]类型不正确，应为 " + targetType;
        } else if (cause instanceof MismatchedInputException) {
            message = "请求体字段结构不正确，请检查必填字段和 JSON 层级";
        } else if (cause instanceof JsonParseException) {
            message = "请求体不是合法的 JSON 格式，请检查逗号、引号和括号";
        } else {
            message = ExceptionMessageUtils.buildDetailedMessage("请求体读取失败", e);
        }
        log.warn("Request ID: {}, MessageNotReadable: {}", request.getRequestId(), message);
        return Result.error(400, message);
    }

    /**
     * 处理方法参数类型不匹配异常。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<?> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String requiredType = e.getRequiredType() == null ? "目标类型" : e.getRequiredType().getSimpleName();
        String message = "参数[" + e.getName() + "]类型不正确，收到的值为[" + e.getValue() + "]，期望类型为 " + requiredType;
        log.warn("Request ID: {}, TypeMismatch: {}", request.getRequestId(), message);
        return Result.error(400, message);
    }

    /**
     * 处理缺少请求参数异常。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<?> handleMissingRequestParameter(MissingServletRequestParameterException e, HttpServletRequest request) {
        String message = "缺少请求参数[" + e.getParameterName() + "]，参数类型应为 " + e.getParameterType();
        log.warn("Request ID: {}, MissingRequestParameter: {}", request.getRequestId(), message);
        return Result.error(400, message);
    }

    /**
     * 处理缺少路径变量异常。
     */
    @ExceptionHandler(MissingPathVariableException.class)
    public Result<?> handleMissingPathVariable(MissingPathVariableException e, HttpServletRequest request) {
        String message = "缺少路径参数[" + e.getVariableName() + "]，请检查接口地址";
        log.warn("Request ID: {}, MissingPathVariable: {}", request.getRequestId(), message);
        return Result.error(400, message);
    }

    /**
     * 处理约束校验异常。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        String message = e.getConstraintViolations() == null || e.getConstraintViolations().isEmpty()
            ? "请求参数校验失败"
            : e.getConstraintViolations().stream()
                .map(this::formatConstraintViolation)
                .collect(Collectors.joining("；"));
        log.warn("Request ID: {}, ConstraintViolation: {}", request.getRequestId(), message);
        return Result.error(400, message);
    }

    /**
     * 处理 Spring 方法级校验异常。
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public Result<?> handleHandlerMethodValidation(HandlerMethodValidationException e, HttpServletRequest request) {
        String message = e.getAllErrors() == null || e.getAllErrors().isEmpty()
            ? "请求参数校验失败"
            : e.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .filter(org.springframework.util.StringUtils::hasText)
                .collect(Collectors.joining("；"));
        if (!org.springframework.util.StringUtils.hasText(message)) {
            message = "请求参数校验失败";
        }
        log.warn("Request ID: {}, HandlerMethodValidation: {}", request.getRequestId(), message);
        return Result.error(400, message);
    }

    /**
     * 处理不支持的媒体类型异常。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public Result<?> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        String contentType = e.getContentType() == null ? "未知" : e.getContentType().toString();
        String message = "请求内容类型[" + contentType + "]不受支持，请确认接口要求的 Content-Type";
        log.warn("Request ID: {}, MediaTypeNotSupported: {}", request.getRequestId(), message);
        return Result.error(415, message);
    }

    /**
     * 处理文件上传过大异常。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<?> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e, HttpServletRequest request) {
        String message = "上传文件超过系统允许大小，请压缩文件后重试";
        log.warn("Request ID: {}, MaxUploadSizeExceeded: {}", request.getRequestId(), message);
        return Result.error(400, message);
    }

    /**
     * 处理文件上传异常。
     */
    @ExceptionHandler(MultipartException.class)
    public Result<?> handleMultipartException(MultipartException e, HttpServletRequest request) {
        String message = ExceptionMessageUtils.buildDetailedMessage("文件上传失败", e);
        log.warn("Request ID: {}, MultipartException: {}", request.getRequestId(), message);
        return Result.error(400, message);
    }

    /**
     * 处理无权限访问异常。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<?> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        String message = "当前用户没有权限访问接口[" + request.getRequestURI() + "]";
        log.warn("Request ID: {}, AccessDenied: {}", request.getRequestId(), message);
        return Result.error(403, message);
    }

    /**
     * 处理数据库约束异常。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<?> handleDataIntegrityViolation(DataIntegrityViolationException e, HttpServletRequest request) {
        String message = ExceptionMessageUtils.buildDetailedMessage("数据保存失败，可能存在重复数据或关联约束限制", e);
        log.warn("Request ID: {}, DataIntegrityViolation: {}", request.getRequestId(), message);
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
        String prefix = "系统处理请求[" + buildRequestSummary(request) + "]时发生内部错误";
        String message = ExceptionMessageUtils.buildDetailedMessage(prefix, e);
        if (prefix.equals(message)) {
            message = prefix + "，请稍后重试";
        }
        return Result.error(500, message);
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
        return Result.error(404, "请求地址不存在：" + e.getResourcePath());
    }

    /**
     * 格式化约束校验错误。
     */
    private String formatConstraintViolation(ConstraintViolation<?> violation) {
        if (violation == null) {
            return "请求参数校验失败";
        }
        String path = violation.getPropertyPath() == null ? "参数" : violation.getPropertyPath().toString();
        String message = violation.getMessage();
        return "参数[" + path + "]校验失败：" + message;
    }

    /**
     * 构建请求摘要。
     */
    private String buildRequestSummary(HttpServletRequest request) {
        if (request == null) {
            return "未知请求";
        }
        String method = request.getMethod() == null ? "UNKNOWN" : request.getMethod();
        String uri = request.getRequestURI() == null ? "" : request.getRequestURI();
        return method + " " + uri;
    }
}
