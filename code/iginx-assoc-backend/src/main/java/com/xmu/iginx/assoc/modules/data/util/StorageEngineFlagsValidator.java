package com.xmu.iginx.assoc.modules.data.util;

import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;

import java.util.regex.Pattern;

/**
 * 存储引擎标记参数校验。
 */
public final class StorageEngineFlagsValidator {

    private static final Pattern HAS_DATA_PATTERN = Pattern.compile("\\bhas_data\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern READ_ONLY_PATTERN = Pattern.compile("\\bis_read_only\\s*=", Pattern.CASE_INSENSITIVE);

    private StorageEngineFlagsValidator() {
    }

    public static void validate(DataSourceConnectionConfig config) {
        if (config == null) {
            return;
        }
        Boolean hasData = config.getHasData();
        Boolean readOnly = config.getReadOnly();
        if (Boolean.FALSE.equals(hasData) && Boolean.TRUE.equals(readOnly)) {
            throw BizException.badRequest("无数据不可只读");
        }
        String extra = config.getExtra();
        if (containsFlag(extra, HAS_DATA_PATTERN) || containsFlag(extra, READ_ONLY_PATTERN)) {
            throw BizException.badRequest("extra 中禁止设置 has_data/is_read_only，请使用明确字段");
        }
    }

    private static boolean containsFlag(String extra, Pattern pattern) {
        if (extra == null || extra.isBlank()) {
            return false;
        }
        return pattern.matcher(extra).find();
    }
}
