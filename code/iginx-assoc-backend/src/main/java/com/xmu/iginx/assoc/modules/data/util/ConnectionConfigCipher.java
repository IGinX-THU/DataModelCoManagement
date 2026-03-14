package com.xmu.iginx.assoc.modules.data.util;

import cn.hutool.crypto.symmetric.AES;
import cn.hutool.crypto.SecureUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.framework.crypto.CryptoConfig;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 数据源连接配置加解密工具。
 */
@Component
@RequiredArgsConstructor
public class ConnectionConfigCipher {

    private final ObjectMapper objectMapper;
    private final CryptoConfig cryptoConfig;

    /**
     * 加密连接配置。
     *
     * @param connectionConfig 连接配置
     * @return 加密后的字符串
     */
    public String encrypt(DataSourceConnectionConfig connectionConfig) {
        try {
            String json = objectMapper.writeValueAsString(connectionConfig);
            return aes().encryptBase64(json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw BizException.internal("连接配置加密失败");
        }
    }

    /**
     * 解密连接配置。
     *
     * @param cipherText 密文
     * @return 连接配置
     */
    public DataSourceConnectionConfig decrypt(String cipherText) {
        try {
            String json = aes().decryptStr(cipherText, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, DataSourceConnectionConfig.class);
        } catch (Exception e) {
            throw BizException.internal("连接配置解密失败");
        }
    }

    /**
     * 构建 AES 实例。
     *
     * @return AES 实例
     */
    private AES aes() {
        byte[] key = normalizeKey(cryptoConfig.getAesKey());
        return SecureUtil.aes(key);
    }

    /**
     * 规范化密钥长度为 16 字节。
     *
     * @param rawKey 原始密钥
     * @return 规范化后的密钥
     */
    private byte[] normalizeKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw BizException.internal("缺少加密密钥配置 assoc.crypto.aes-key");
        }
        byte[] rawBytes = rawKey.getBytes(StandardCharsets.UTF_8);
        byte[] normalized = new byte[16];
        int copyLength = Math.min(rawBytes.length, normalized.length);
        // 截断或补零到 16 字节
        System.arraycopy(rawBytes, 0, normalized, 0, copyLength);
        return normalized;
    }

    /**
     * 脱敏显示密码，仅保留首尾字符。
     *
     * @param encryptedConfig 加密后的配置
     * @return 脱敏密码
     */
    public String maskPassword(String encryptedConfig) {
        DataSourceConnectionConfig config = decrypt(encryptedConfig);
        String password = config.getPassword();
        if (password == null || password.isEmpty()) {
            return "";
        }
        if (password.length() <= 2) {
            return "**";
        }
        return password.charAt(0) + "***" + password.charAt(password.length() - 1);
    }
}
