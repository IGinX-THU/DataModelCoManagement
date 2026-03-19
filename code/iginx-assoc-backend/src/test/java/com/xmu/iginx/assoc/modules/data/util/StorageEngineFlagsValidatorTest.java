package com.xmu.iginx.assoc.modules.data.util;

import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 存储引擎标志位校验测试。
 */
class StorageEngineFlagsValidatorTest {

    /**
     * 无历史数据时，不允许只读模式。
     */
    @Test
    void validate_shouldRejectReadOnlyWithoutData() {
        DataSourceConnectionConfig config = new DataSourceConnectionConfig();
        config.setHasData(false);
        config.setReadOnly(true);

        BizException ex = assertThrows(BizException.class, () -> StorageEngineFlagsValidator.validate(config));
        assertTrue(ex.getMessage().contains("无数据时不允许只读"));
    }

    /**
     * extra 中不允许再传保留标志位。
     */
    @Test
    void validate_shouldRejectFlagsInExtra() {
        DataSourceConnectionConfig config = new DataSourceConnectionConfig();
        config.setHasData(true);
        config.setReadOnly(false);
        config.setExtra("has_data=true");

        BizException ex = assertThrows(BizException.class, () -> StorageEngineFlagsValidator.validate(config));
        assertTrue(ex.getMessage().contains("extra"));
    }

    /**
     * extra 中不允许再传 schema/data 前缀参数。
     */
    @Test
    void validate_shouldRejectPrefixFlagsInExtra() {
        DataSourceConnectionConfig config = new DataSourceConnectionConfig();
        config.setHasData(true);
        config.setReadOnly(false);
        config.setExtra("schema_prefix=demo,data_prefix=ts");

        BizException ex = assertThrows(BizException.class, () -> StorageEngineFlagsValidator.validate(config));
        assertTrue(ex.getMessage().contains("schema_prefix"));
    }

    /**
     * 合法组合应通过校验。
     */
    @Test
    void validate_shouldAllowValidCombination() {
        DataSourceConnectionConfig config = new DataSourceConnectionConfig();
        config.setHasData(true);
        config.setReadOnly(false);
        config.setSchemaPrefix("project_a");
        config.setDataPrefix("ts");
        config.setExtra("database=demo");

        assertDoesNotThrow(() -> StorageEngineFlagsValidator.validate(config));
    }
}
