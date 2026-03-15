package com.xmu.iginx.assoc.framework.iginx;

import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.data.util.DataPrefixRules;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * IGinX FileSystem 存储引擎注册器，负责在启动阶段自动注册存储引擎。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IginxFileSystemRegistrar {

    private final IginxStorageWrapper storageWrapper;
    private final IginxConfig iginxConfig;
    private final IginxFileSystemConfig fileSystemConfig;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    /**
     * 容器启动后触发注册逻辑。
     */
    @PostConstruct
    public void init() {
        ensureRegistered();
    }

    /**
     * 获取 FileSystem 数据前缀。
     *
     * @return 数据前缀
     */
    public String getDataPrefix() {
        return resolveModelPrefix();
    }

    /**
     * 确保存储引擎已注册，未满足条件时直接返回。
     */
    public void ensureRegistered() {
        // 配置未启用或不允许自动注册时直接跳过
        if (!fileSystemConfig.isEnabled() || !fileSystemConfig.isAutoRegister()) {
            return;
        }
        // 已注册则避免重复执行
        if (registered.get()) {
            return;
        }
        String addSql = buildAddStorageEngineSql();
        if (!StringUtils.hasText(addSql)) {
            return;
        }
        try {
            storageWrapper.executeSql(addSql);
            registered.set(true);
            log.info("已注册 IGinX FileSystem 存储引擎: {}:{}", fileSystemConfig.getHost(), fileSystemConfig.getPort());
        } catch (BizException ex) {
            log.warn("注册 IGinX FileSystem 存储引擎失败，稍后重试。", ex);
        } catch (Exception ex) {
            log.warn("注册 IGinX FileSystem 存储引擎失败，稍后重试。", ex);
        }
    }

    /**
     * 构建添加存储引擎的 SQL。
     *
     * @return SQL 字符串；若参数不完整则返回 null
     */
    private String buildAddStorageEngineSql() {
        String host = fileSystemConfig.getHost();
        int port = fileSystemConfig.getPort();
        if (!StringUtils.hasText(host) || port <= 0) {
            log.warn("FileSystem 存储引擎地址未配置，跳过注册。");
            return null;
        }
        String extra = fileSystemConfig.getExtra();
        List<String> params = new ArrayList<>();
        // 按需补齐默认参数，避免覆盖用户显式配置
        appendParamIfAbsent(params, extra, "has_data", "false");
        appendParamIfAbsent(params, extra, "is_read_only", "false");
        String modelPrefix = resolveModelPrefix();
        if (StringUtils.hasText(modelPrefix) && !containsParam(extra, "data_prefix")) {
            params.add("data_prefix=" + modelPrefix);
        }
        String dir = fileSystemConfig.getDir();
        if (!StringUtils.hasText(dir) && !containsParam(extra, "dir")) {
            dir = "data";
        }
        if (StringUtils.hasText(dir) && !containsParam(extra, "dir")) {
            params.add("dir=" + dir.trim());
        }
        appendParamIfAbsent(params, extra, "iginx_port", String.valueOf(iginxConfig.getPort()));
        if (StringUtils.hasText(extra)) {
            params.add(extra.trim());
        }
        String paramString = String.join(", ", params);
        return String.format("ADD STORAGEENGINE (\"%s\", %d, \"filesystem\", \"%s\");",
            host.trim(), port, escape(paramString));
    }

    /**
     * 规范化模型前缀，确保为 models。
     *
     * @return 合法的模型前缀
     */
    private String resolveModelPrefix() {
        String raw = fileSystemConfig.getDataPrefix();
        String normalized = DataPrefixRules.normalizeModelPrefix(raw);
        if (!DataPrefixRules.isModelPrefix(raw)) {
            log.warn("检测到非法模型前缀：{}，已自动重置为 {}", raw, DataPrefixRules.MODEL_PREFIX);
        }
        return normalized;
    }

    /**
     * 若参数未出现，则追加默认参数。
     *
     * @param params 已收集参数
     * @param extra 额外参数字符串
     * @param key 参数名
     * @param value 参数值
     */
    private void appendParamIfAbsent(List<String> params, String extra, String key, String value) {
        if (containsParam(extra, key)) {
            return;
        }
        params.add(key + "=" + value);
    }

    /**
     * 判断 extra 字符串中是否已包含指定参数。
     *
     * @param extra 额外参数字符串
     * @param key 参数名
     * @return 是否存在
     */
    private boolean containsParam(String extra, String key) {
        if (!StringUtils.hasText(extra) || !StringUtils.hasText(key)) {
            return false;
        }
        return extra.toLowerCase(Locale.ROOT).contains(key.toLowerCase(Locale.ROOT) + "=");
    }

    /**
     * 对 SQL 字符串中的特殊字符进行转义。
     *
     * @param value 原始字符串
     * @return 转义后字符串
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'");
    }
}
