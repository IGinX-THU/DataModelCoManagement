package com.xmu.iginx.assoc.modules.data.util;

import com.xmu.iginx.assoc.common.exception.BizException;
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
 * IGinX 存储引擎 SQL 生成辅助工具。
 */
@Component
@RequiredArgsConstructor
public class IginxStorageEngineHelper {

    private final IginxConfig iginxConfig;

    /**
     * 构建添加存储引擎的 SQL 语句。
     *
     * @param sourceType 数据源类型
     * @param config 数据源连接配置
     * @return SQL 字符串
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
     * 构建卸载存储引擎的 SQL 语句。
     * <p>
     * 依据用户手册 3.3.5：REMOVE STORAGEENGINE (ip, port, schemaPrefix, dataPrefix)。
     * schemaPrefix/dataPrefix 为空时需传空字符串。
     * </p>
     *
     * @param config 数据源连接配置
     * @return SQL 字符串
     */
    public String buildRemoveStorageEngineSql(DataSourceConnectionConfig config) {
        if (config == null) {
            throw BizException.badRequest("卸载数据源失败：连接配置不能为空");
        }
        String resolvedHost = resolveStorageHost(config.getHost());
        Integer port = config.getPort();
        if (!StringUtils.hasText(resolvedHost) || port == null || port < 0 || port > 65535) {
            throw BizException.badRequest("卸载数据源失败：主机或端口无效");
        }
        String schemaPrefix = normalizePrefix(config.getSchemaPrefix());
        String dataPrefix = normalizePrefix(config.getDataPrefix());
        return String.format("REMOVE STORAGEENGINE (\"%s\", %d, \"%s\", \"%s\");",
            escape(resolvedHost), port, escape(schemaPrefix), escape(dataPrefix));
    }

    /**
     * 解析存储引擎连接地址。
     *
     * @param host 存储地址
     * @return 解析后的存储地址
     */
    public String resolveStorageHost(String host) {
        return resolveHost(host);
    }

    /**
     * 解析存储引擎类型。
     *
     * @param sourceType 数据源类型
     * @return 引擎类型字符串
     */
    public String resolveEngineType(DataSourceType sourceType) {
        return toEngineType(sourceType);
    }

    /**
     * 构建存储引擎的额外参数。
     *
     * @param sourceType 数据源类型
     * @param config 数据源连接配置
     * @param resolvedHost 解析后的存储地址
     * @return 额外参数字符串
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
        if (StringUtils.hasText(config.getSchemaPrefix())) {
            params.add("schema_prefix=" + config.getSchemaPrefix().trim());
        }
        if (StringUtils.hasText(config.getDataPrefix())) {
            params.add("data_prefix=" + config.getDataPrefix().trim());
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
        if (extra != null && !extra.isBlank()) {
            params.add(extra);
        }
        return String.join(", ", params);
    }

    /**
     * 解析存储地址，必要时用配置中的地址替换本地地址。
     *
     * @param host 存储地址
     * @return 解析后的存储地址
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
     * 规范化前缀参数，空值统一为空字符串。
     *
     * @param prefix 前缀值
     * @return 规范化后的前缀
     */
    private String normalizePrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return "";
        }
        return prefix.trim();
    }

    /**
     * 将数据源类型映射为 IGinX 引擎类型。
     *
     * @param sourceType 数据源类型
     * @return 引擎类型字符串
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
