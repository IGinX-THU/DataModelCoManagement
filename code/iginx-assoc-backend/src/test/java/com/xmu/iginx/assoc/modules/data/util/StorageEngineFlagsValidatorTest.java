package com.xmu.iginx.assoc.modules.data.util;

import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageEngineFlagsValidatorTest {

    @Test
    void validate_shouldRejectReadOnlyWithoutData() {
        DataSourceConnectionConfig config = new DataSourceConnectionConfig();
        config.setHasData(false);
        config.setReadOnly(true);

        BizException ex = assertThrows(BizException.class, () -> StorageEngineFlagsValidator.validate(config));
        assertTrue(ex.getMessage().contains("无数据不可只读"));
    }

    @Test
    void validate_shouldRejectFlagsInExtra() {
        DataSourceConnectionConfig config = new DataSourceConnectionConfig();
        config.setHasData(true);
        config.setReadOnly(false);
        config.setExtra("has_data=true");

        BizException ex = assertThrows(BizException.class, () -> StorageEngineFlagsValidator.validate(config));
        assertTrue(ex.getMessage().contains("extra"));
    }

    @Test
    void validate_shouldAllowValidCombination() {
        DataSourceConnectionConfig config = new DataSourceConnectionConfig();
        config.setHasData(true);
        config.setReadOnly(false);
        config.setExtra("database=demo");

        assertDoesNotThrow(() -> StorageEngineFlagsValidator.validate(config));
    }
}
