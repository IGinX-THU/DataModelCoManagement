package com.xmu.iginx.assoc.modules.data.util;

import com.xmu.iginx.assoc.framework.iginx.IginxConfig;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;
import com.xmu.iginx.assoc.modules.data.enums.DataSourceType;
import com.xmu.iginx.assoc.modules.data.util.TimeSeriesPathUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * IGinX 存储引擎 SQL 构建与参数辅助工具。
 */
@Component
@RequiredArgsConstructor
public class IginxStorageEngineHelper {

    private final IginxConfig iginxConfig;

    /**
     * 构建添加存储引擎 SQL。
     *
     * @param sourceType 数据源类型
     * @param config 连接配置
     * @param mountPath 挂载路径
     * @return SQL 语句
     */
    public String buildAddStorageEngineSql(DataSourceType sourceType,
                                           DataSourceConnectionConfig config,
                                           String mountPath) {
        String resolvedHost = resolveStorageHost(config.getHost());
        String engine = resolveEngineType(sourceType);
        String extraParams = buildExtraParams(sourceType, config, mountPath, resolvedHost);
        return String.format("ADD STORAGEENGINE (\"%s\", %d, \"%s\", \"%s\");",
            resolvedHost, config.getPort(), engine, escape(extraParams));
    }

    /**
     * 构建移除存储引擎 SQL。
     *
     * @param config 连接配置
     * @param schemaPrefix schema 前缀
     * @param dataPrefix data 前缀
     * @param forAll 是否对所有分片生效
     * @return SQL 语句
     */
    public String buildRemoveStorageEngineSql(DataSourceConnectionConfig config,
                                              String schemaPrefix,
                                              String dataPrefix,
                                              boolean forAll) {
        String resolvedHost = resolveStorageHost(config.getHost());
        String rawHost = config.getHost() == null ? "" : config.getHost().trim();
        String normalizedSchemaPrefix = schemaPrefix == null ? "" : schemaPrefix;
        String normalizedDataPrefix = dataPrefix == null ? "" : dataPrefix;
        if ("host.docker.internal".equalsIgnoreCase(rawHost)) {
            // docker 场景需保留原始 host
            return String.format("REMOVE STORAGEENGINE (\"%s\", %d, \"%s\", \"%s\")%s;",
                rawHost, config.getPort(), escape(normalizedSchemaPrefix), escape(normalizedDataPrefix),
                forAll ? " FOR ALL" : "");
        }
        return String.format("REMOVE STORAGEENGINE (\"%s\", %d, \"%s\", \"%s\")%s;",
            resolvedHost, config.getPort(), escape(normalizedSchemaPrefix), escape(normalizedDataPrefix),
            forAll ? " FOR ALL" : "");
    }

    /**
     * 解析存储引擎主机地址。
     *
     * @param host 原始主机
     * @return 解析后的主机
     */
    public String resolveStorageHost(String host) {
        return resolveHost(host);
    }

    /**
     * 解析存储引擎类型。
     *
     * @param sourceType 数据源类型
     * @return 引擎类型
     */
    public String resolveEngineType(DataSourceType sourceType) {
        return toEngineType(sourceType);
    }

    /**
     * 构建存储引擎扩展参数。
     *
     * @param sourceType 数据源类型
     * @param config 连接配置
     * @param mountPath 挂载路径
     * @param resolvedHost 解析后的主机
     * @return 参数字符串
     */
    private String buildExtraParams(DataSourceType sourceType,
                                    DataSourceConnectionConfig config,
                                    String mountPath,
                                    String resolvedHost) {
        List<String> params = new ArrayList<>();
        if (sourceType == DataSourceType.IOTDB) {
            mountPath = TimeSeriesPathUtils.normalizeIotdbMountPath(mountPath);
        }
        String extra = config.getExtra();
        Boolean hasDataValue = config.getHasData();
        Boolean readOnlyValue = config.getReadOnly();
        // IGinX 0.8.0 ?? has_data=true ??? data_prefix????????????? true
        boolean defaultHasData = StringUtils.hasText(mountPath);
        boolean hasData = hasDataValue != null ? hasDataValue : defaultHasData;
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
        if (hasData && mountPath != null && !mountPath.isBlank()) {
            params.add("data_prefix=" + mountPath);
        }
        if (extra != null && !extra.isBlank()) {
            params.add(extra);
        }
        return String.join(", ", params);
    }

    /**
     * 解析主机地址，支持本地替换。
     *
     * @param host 原始主机
     * @return 解析后的主机
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
     * 将数据源类型映射为 IGinX 引擎类型。
     *
     * @param sourceType 数据源类型
     * @return 引擎类型
     */
    private String toEngineType(DataSourceType sourceType) {
        return switch (sourceType) {
            case INFLUXDB -> "influxdb";
            case IOTDB -> "iotdb12";
            case POSTGRESQL -> "relational";
        };
    }

    /**
     * 转义 SQL 参数中的特殊字符。
     *
     * @param value 原始值
     * @return 转义后的值
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'");
    }
}
