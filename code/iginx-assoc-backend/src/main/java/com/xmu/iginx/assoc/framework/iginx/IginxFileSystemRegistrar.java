package com.xmu.iginx.assoc.framework.iginx;

import com.xmu.iginx.assoc.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class IginxFileSystemRegistrar {

    private final IginxStorageWrapper storageWrapper;
    private final IginxConfig iginxConfig;
    private final IginxFileSystemConfig fileSystemConfig;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        ensureRegistered();
    }

    public String getDataPrefix() {
        return fileSystemConfig.getDataPrefix();
    }

    public void ensureRegistered() {
        if (!fileSystemConfig.isEnabled() || !fileSystemConfig.isAutoRegister()) {
            return;
        }
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

    private String buildAddStorageEngineSql() {
        String host = fileSystemConfig.getHost();
        int port = fileSystemConfig.getPort();
        if (!StringUtils.hasText(host) || port <= 0) {
            log.warn("FileSystem 存储引擎地址未配置，跳过注册。");
            return null;
        }
        String extra = fileSystemConfig.getExtra();
        List<String> params = new ArrayList<>();
        appendParamIfAbsent(params, extra, "has_data", "false");
        appendParamIfAbsent(params, extra, "is_read_only", "false");
        if (StringUtils.hasText(fileSystemConfig.getDataPrefix()) && !containsParam(extra, "data_prefix")) {
            params.add("data_prefix=" + fileSystemConfig.getDataPrefix().trim());
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

    private void appendParamIfAbsent(List<String> params, String extra, String key, String value) {
        if (containsParam(extra, key)) {
            return;
        }
        params.add(key + "=" + value);
    }

    private boolean containsParam(String extra, String key) {
        if (!StringUtils.hasText(extra) || !StringUtils.hasText(key)) {
            return false;
        }
        return extra.toLowerCase(Locale.ROOT).contains(key.toLowerCase(Locale.ROOT) + "=");
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'");
    }
}
