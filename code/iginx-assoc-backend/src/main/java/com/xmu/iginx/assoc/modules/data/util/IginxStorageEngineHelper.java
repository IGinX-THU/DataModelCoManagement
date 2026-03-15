package com.xmu.iginx.assoc.modules.data.util;

import com.xmu.iginx.assoc.framework.iginx.IginxConfig;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;
import com.xmu.iginx.assoc.modules.data.enums.DataSourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * IGinX 瀛樺偍寮曟搸 SQL 鏋勫缓涓庡弬鏁拌緟鍔╁伐鍏枫€?
 */
@Component
@RequiredArgsConstructor
public class IginxStorageEngineHelper {

    private final IginxConfig iginxConfig;

    /**
     * 鏋勫缓娣诲姞瀛樺偍寮曟搸 SQL銆?
     *
     * @param sourceType 鏁版嵁婧愮被鍨?
     * @param config 杩炴帴閰嶇疆
     * @return SQL 璇彞
     */
    public String buildAddStorageEngineSql(DataSourceType sourceType,
                                           DataSourceConnectionConfig config) {
        String resolvedHost = resolveStorageHost(config.getHost());
        String engine = resolveEngineType(sourceType);
        String extraParams = buildExtraParams(sourceType, config, resolvedHost);
        return String.format("ADD STORAGEENGINE (\"%s\", %d, \"%s\", \"%s\");",
            resolvedHost, config.getPort(), engine, escape(extraParams));
    }

    /**
     * 瑙ｆ瀽瀛樺偍寮曟搸涓绘満鍦板潃銆?
     *
     * @param host 鍘熷涓绘満
     * @return 瑙ｆ瀽鍚庣殑涓绘満
     */
    public String resolveStorageHost(String host) {
        return resolveHost(host);
    }

    /**
     * 瑙ｆ瀽瀛樺偍寮曟搸绫诲瀷銆?
     *
     * @param sourceType 鏁版嵁婧愮被鍨?
     * @return 寮曟搸绫诲瀷
     */
    public String resolveEngineType(DataSourceType sourceType) {
        return toEngineType(sourceType);
    }

    /**
     * 鏋勫缓瀛樺偍寮曟搸鎵╁睍鍙傛暟銆?
     *
     * @param sourceType 鏁版嵁婧愮被鍨?
     * @param config 杩炴帴閰嶇疆
     * @param resolvedHost 瑙ｆ瀽鍚庣殑涓绘満
     * @return 鍙傛暟瀛楃涓?
     */
    private String buildExtraParams(DataSourceType sourceType,
                                    DataSourceConnectionConfig config,
                                    String resolvedHost) {
        List<String> params = new ArrayList<>();
        String extra = config.getExtra();
        Boolean hasDataValue = config.getHasData();
        Boolean readOnlyValue = config.getReadOnly();
        boolean hasData = Boolean.TRUE.equals(hasDataValue);
        boolean readOnly = readOnlyValue != null ? readOnlyValue : false;
        params.add("has_data=" + hasData);
        params.add("is_read_only=" + readOnly);
        if (sourceType == DataSourceType.INFLUXDB) {
            params.add(String.format("url=http://%s:%d/", resolvedHost, config.getPort()));
        }
        if (sourceType == DataSourceType.POSTGRESQL) {
            params.add("engine=postgresql");
        }
        if (config.getUsername() != null && !config.getUsername().isBlank()) {
            params.add("username=" + config.getUsername());
        }
        if (config.getPassword() != null && !config.getPassword().isBlank()) {
            params.add("password=" + config.getPassword());
        }
        if (config.getDatabase() != null && !config.getDatabase().isBlank()) {
            params.add("database=" + config.getDatabase());
        }
        if (extra != null && !extra.isBlank()) {
            params.add(extra);
        }
        return String.join(", ", params);
    }

    /**
     * 瑙ｆ瀽涓绘満鍦板潃锛屾敮鎸佹湰鍦版浛鎹€?
     *
     * @param host 鍘熷涓绘満
     * @return 瑙ｆ瀽鍚庣殑涓绘満
     */
    private String resolveHost(String host) {
        if (!StringUtils.hasText(host)) {
            return host;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        boolean isLocal = "127.0.0.1".equals(normalized) || "localhost".equals(normalized);
        if (isLocal && StringUtils.hasText(iginxConfig.getStorageHostOverride())) {
            return iginxConfig.getStorageHostOverride().trim();
        }
        return host.trim();
    }

    /**
     * 灏嗘暟鎹簮绫诲瀷鏄犲皠涓?IGinX 寮曟搸绫诲瀷銆?
     *
     * @param sourceType 鏁版嵁婧愮被鍨?
     * @return 寮曟搸绫诲瀷
     */
    private String toEngineType(DataSourceType sourceType) {
        return switch (sourceType) {
            case INFLUXDB -> "influxdb";
            case IOTDB -> "iotdb12";
            case POSTGRESQL -> "relational";
        };
    }

    /**
     * 杞箟 SQL 鍙傛暟涓殑鐗规畩瀛楃銆?
     *
     * @param value 鍘熷鍊?
     * @return 杞箟鍚庣殑鍊?
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'");
    }
}

