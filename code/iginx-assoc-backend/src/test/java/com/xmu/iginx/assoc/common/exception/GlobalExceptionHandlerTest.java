package com.xmu.iginx.assoc.common.exception;

import com.xmu.iginx.assoc.common.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全局异常处理器测试。
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    /**
     * 不支持的请求方法应返回包含接口地址与方法名的中文提示。
     */
    @Test
    void handleMethodNotSupported_shouldReturnChineseMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/task-chains/runs/run_1");
        HttpRequestMethodNotSupportedException exception =
            new HttpRequestMethodNotSupportedException("DELETE", java.util.List.of("GET", "POST"));

        Result<?> result = handler.handleMethodNotSupported(exception, request);

        assertEquals(405, result.getCode());
        assertTrue(result.getMsg().contains("/api/v1/task-chains/runs/run_1"));
        assertTrue(result.getMsg().contains("DELETE"));
    }

    /**
     * 缺少请求参数应返回具体参数名。
     */
    @Test
    void handleMissingRequestParameter_shouldReturnParameterName() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tasks");
        MissingServletRequestParameterException exception =
            new MissingServletRequestParameterException("ruleId", "Long");

        Result<?> result = handler.handleMissingRequestParameter(exception, request);

        assertEquals(400, result.getCode());
        assertTrue(result.getMsg().contains("ruleId"));
        assertTrue(result.getMsg().contains("Long"));
    }

    /**
     * 未知异常应尽量返回中文可读原因。
     */
    @Test
    void handleException_shouldReturnReadableChineseDetail() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/models");
        RuntimeException exception = new RuntimeException("Connection refused: connect");

        Result<?> result = handler.handleException(exception, request);

        assertEquals(500, result.getCode());
        assertTrue(result.getMsg().contains("POST /api/v1/models"));
        assertTrue(result.getMsg().contains("连接被拒绝"));
    }
}
