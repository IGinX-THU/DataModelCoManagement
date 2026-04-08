package com.xmu.iginx.assoc.modules.data.service;

import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.common.exception.ExceptionMessageUtils;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;
import com.xmu.iginx.assoc.modules.data.enums.DataSourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

/**
 * 数据源连接测试服务。
 */
@Slf4j
@Service
public class DataSourceConnectionTestService {

    /**
     * 测试数据源连接。
     *
     * @param sourceType 数据源类型
     * @param config 连接配置
     */
    public void testConnection(String sourceType, DataSourceConnectionConfig config) {
        if (!DataSourceType.isSupported(sourceType)) {
            throw BizException.badRequest("不支持的数据源类型: " + sourceType);
        }
        verifySocketConnectivity(config.getHost(), config.getPort(), Duration.ofSeconds(3));
    }

    /**
     * 验证主机端口连通性。
     *
     * @param host 主机
     * @param port 端口
     * @param timeout 超时时间
     */
    private void verifySocketConnectivity(String host, Integer port, Duration timeout) {
        String testHost = host;
        if ("host.docker.internal".equalsIgnoreCase(host)) {
            // docker 场景下转为本机地址探测
            testHost = "127.0.0.1";
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(testHost, port), (int) timeout.toMillis());
        } catch (Exception e) {
            log.warn("连接测试失败 host={}, port={}, err={}", host, port, e.getMessage());
            throw BizException.badRequest(ExceptionMessageUtils.buildDetailedMessage("连接测试失败", e), e);
        }
    }
}
