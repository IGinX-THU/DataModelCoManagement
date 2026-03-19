package com.xmu.iginx.assoc.modules.data.util;

import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;

import java.util.regex.Pattern;

/**
 * 存储引擎标志参数校验工具。
 */
public final class StorageEngineFlagsValidator {

    private static final Pattern HAS_DATA_PATTERN = Pattern.compile("\\bhas_data\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern READ_ONLY_PATTERN = Pattern.compile("\\bis_read_only\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCHEMA_PREFIX_PATTERN = Pattern.compile("\\bschema_?prefix\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATA_PREFIX_PATTERN = Pattern.compile("\\bdata_?prefix\\s*=", Pattern.CASE_INSENSITIVE);

    private StorageEngineFlagsValidator() {
    }

    /**
     * 校验新增存储引擎参数。
     *
     * @param config 连接配置
     */
    public static void validate(DataSourceConnectionConfig config) {
        if (config == null) {
            return;
        }
        Boolean hasData = config.getHasData();
        Boolean readOnly = config.getReadOnly();
        if (Boolean.FALSE.equals(hasData) && Boolean.TRUE.equals(readOnly)) {
            throw BizException.badRequest("无数据时不允许只读，请调整 hasData/readOnly 组合");
        }

        String extra = config.getExtra();
        if (containsFlag(extra, HAS_DATA_PATTERN)
            || containsFlag(extra, READ_ONLY_PATTERN)
            || containsFlag(extra, SCHEMA_PREFIX_PATTERN)
            || containsFlag(extra, DATA_PREFIX_PATTERN)) {
            throw BizException.badRequest("extra 中禁止设置 has_data/is_read_only/schema_prefix/data_prefix，请使用独立字段");
        }
    }

    private static boolean containsFlag(String extra, Pattern pattern) {
        if (extra == null || extra.isBlank()) {
            return false;
        }
        return pattern.matcher(extra).find();
    }
}
