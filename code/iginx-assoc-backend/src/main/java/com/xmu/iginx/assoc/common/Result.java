package com.xmu.iginx.assoc.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用接口返回结果封装。
 *
 * @param <T> 返回数据类型
 */
@Data
public class Result<T> implements Serializable {
    private Integer code;
    private String msg;
    private T data;
    private Long timestamp;

    /**
     * 构造成功响应并携带数据。
     *
     * @param data 业务数据
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMsg("Success");
        r.setData(data);
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }

    /**
     * 构造成功响应（不携带数据）。
     *
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 构造错误响应。
     *
     * @param code 业务错误码
     * @param msg 错误信息
     * @param <T> 数据类型
     * @return 错误响应
     */
    public static <T> Result<T> error(Integer code, String msg) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }
}
