package com.xmu.iginx.assoc.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 异常消息提炼工具测试。
 */
class ExceptionMessageUtilsTest {

    /**
     * 常见英文连接错误应翻译为中文提示。
     */
    @Test
    void extractReadableMessage_shouldTranslateConnectionRefused() {
        String message = ExceptionMessageUtils.extractReadableMessage(
            new RuntimeException("Connection refused: connect")
        );

        assertEquals("连接被拒绝，请检查目标服务是否已启动", message);
    }

    /**
     * 组合消息时应保留业务前缀与底层原因。
     */
    @Test
    void buildDetailedMessage_shouldAppendReadableDetail() {
        String message = ExceptionMessageUtils.buildDetailedMessage(
            "模型文件保存失败",
            new RuntimeException("Access is denied")
        );

        assertTrue(message.contains("模型文件保存失败"));
        assertTrue(message.contains("没有权限访问目标资源"));
    }
}
