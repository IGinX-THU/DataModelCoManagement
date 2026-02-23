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

@Component
@RequiredArgsConstructor
public class IginxStorageEngineHelper {

    private final IginxConfig iginxConfig;

    public String buildAddStorageEngineSql(DataSourceType sourceType,
                                           DataSourceConnectionConfig config,
                                           String mountPath) {
        String resolvedHost = resolveStorageHost(config.getHost());
        String engine = resolveEngineType(sourceType);
        String extraParams = buildExtraParams(sourceType, config, mountPath, resolvedHost);
        return String.format("ADD STORAGEENGINE (\"%s\", %d, \"%s\", \"%s\");",
            resolvedHost, config.getPort(), engine, escape(extraParams));
    }

    public String buildRemoveStorageEngineSql(DataSourceConnectionConfig config,
                                              String schemaPrefix,
                                              String dataPrefix,
                                              boolean forAll) {
        String resolvedHost = resolveStorageHost(config.getHost());
        String rawHost = config.getHost() == null ? "" : config.getHost().trim();
        String normalizedSchemaPrefix = schemaPrefix == null ? "" : schemaPrefix;
        String normalizedDataPrefix = dataPrefix == null ? "" : dataPrefix;
        if ("host.docker.internal".equalsIgnoreCase(rawHost)) {
            return String.format("REMOVE STORAGEENGINE (\"%s\", %d, \"%s\", \"%s\")%s;",
                rawHost, config.getPort(), escape(normalizedSchemaPrefix), escape(normalizedDataPrefix),
                forAll ? " FOR ALL" : "");
        }
        return String.format("REMOVE STORAGEENGINE (\"%s\", %d, \"%s\", \"%s\")%s;",
            resolvedHost, config.getPort(), escape(normalizedSchemaPrefix), escape(normalizedDataPrefix),
            forAll ? " FOR ALL" : "");
    }

    public String resolveStorageHost(String host) {
        return resolveHost(host);
    }

    public String resolveEngineType(DataSourceType sourceType) {
        return toEngineType(sourceType);
    }

    private String buildExtraParams(DataSourceType sourceType,
                                    DataSourceConnectionConfig config,
                                    String mountPath,
                                    String resolvedHost) {
        List<String> params = new ArrayList<>();
        String extra = config.getExtra();
        boolean hasDataSpecified = containsParam(extra, "has_data");
        boolean readOnlySpecified = containsParam(extra, "is_read_only");
        boolean defaultHasData = true;
        if (!hasDataSpecified) {
            params.add("has_data=" + defaultHasData);
        }
        if (!readOnlySpecified) {
            params.add("is_read_only=false");
        }
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
        if (mountPath != null && !mountPath.isBlank()) {
            params.add("data_prefix=" + mountPath);
        }
        if (extra != null && !extra.isBlank()) {
            params.add(extra);
        }
        return String.join(", ", params);
    }

    private boolean containsParam(String extra, String key) {
        if (extra == null || extra.isBlank() || key == null || key.isBlank()) {
            return false;
        }
        return extra.toLowerCase(Locale.ROOT).contains(key.toLowerCase(Locale.ROOT) + "=");
    }

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

    private String toEngineType(DataSourceType sourceType) {
        return switch (sourceType) {
            case INFLUXDB -> "influxdb";
            case IOTDB -> "iotdb12";
            case POSTGRESQL -> "relational";
        };
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'");
    }
}
