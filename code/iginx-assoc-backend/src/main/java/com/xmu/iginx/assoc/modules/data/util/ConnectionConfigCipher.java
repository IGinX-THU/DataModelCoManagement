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

@Component
@RequiredArgsConstructor
public class ConnectionConfigCipher {

    private final ObjectMapper objectMapper;
    private final CryptoConfig cryptoConfig;

    public String encrypt(DataSourceConnectionConfig connectionConfig) {
        try {
            String json = objectMapper.writeValueAsString(connectionConfig);
            return aes().encryptBase64(json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw BizException.internal("连接配置加密失败");
        }
    }

    public DataSourceConnectionConfig decrypt(String cipherText) {
        try {
            String json = aes().decryptStr(cipherText, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, DataSourceConnectionConfig.class);
        } catch (Exception e) {
            throw BizException.internal("连接配置解密失败");
        }
    }

    private AES aes() {
        byte[] key = normalizeKey(cryptoConfig.getAesKey());
        return SecureUtil.aes(key);
    }

    private byte[] normalizeKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw BizException.internal("缺少加密密钥配置 assoc.crypto.aes-key");
        }
        byte[] rawBytes = rawKey.getBytes(StandardCharsets.UTF_8);
        byte[] normalized = new byte[16];
        int copyLength = Math.min(rawBytes.length, normalized.length);
        System.arraycopy(rawBytes, 0, normalized, 0, copyLength);
        return normalized;
    }

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
