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
     * 无历史数据且只读时应拒绝。
     */
    @Test
    void validate_shouldRejectReadOnlyWithoutData() {
        DataSourceConnectionConfig config = new DataSourceConnectionConfig();
        config.setHasData(false);
        config.setReadOnly(true);

        BizException ex = assertThrows(BizException.class, () -> StorageEngineFlagsValidator.validate(config));
        assertTrue(ex.getMessage().contains("无数据不可只读"));
    }

    /**
     * extra 中携带保留标志位应拒绝。
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
     * 合法组合应通过校验。
     */
    @Test
    void validate_shouldAllowValidCombination() {
        DataSourceConnectionConfig config = new DataSourceConnectionConfig();
        config.setHasData(true);
        config.setReadOnly(false);
        config.setExtra("database=demo");

        assertDoesNotThrow(() -> StorageEngineFlagsValidator.validate(config));
    }
}
