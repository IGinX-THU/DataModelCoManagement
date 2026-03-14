package com.xmu.iginx.assoc.common.exception;

import lombok.Getter;

/**
 * 业务异常，携带 HTTP 风格错误码。
 */
@Getter
public class BizException extends RuntimeException {

    private final Integer code;

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造 400（请求参数错误）异常。
     *
     * @param message 错误信息
     * @return 业务异常
     */
    public static BizException badRequest(String message) {
        return new BizException(400, message);
    }

    /**
     * 构造 401（未授权）异常。
     *
     * @param message 错误信息
     * @return 业务异常
     */
    public static BizException unauthorized(String message) {
        return new BizException(401, message);
    }

    /**
     * 构造 403（禁止访问）异常。
     *
     * @param message 错误信息
     * @return 业务异常
     */
    public static BizException forbidden(String message) {
        return new BizException(403, message);
    }

    /**
     * 构造 500（内部错误）异常。
     *
     * @param message 错误信息
     * @return 业务异常
     */
    public static BizException internal(String message) {
        return new BizException(500, message);
    }

    /**
     * 构造 503（服务繁忙）异常。
     *
     * @param message 错误信息
     * @return 业务异常
     */
    public static BizException busy(String message) {
        return new BizException(503, message);
    }
}
