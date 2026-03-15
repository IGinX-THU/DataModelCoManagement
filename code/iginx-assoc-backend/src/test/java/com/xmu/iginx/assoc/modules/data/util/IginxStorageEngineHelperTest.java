package com.xmu.iginx.assoc.modules.data.util;

import com.xmu.iginx.assoc.framework.iginx.IginxConfig;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;
import com.xmu.iginx.assoc.modules.data.enums.DataSourceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Iginx 存储引擎 SQL 构建测试。
 */
class IginxStorageEngineHelperTest {

    /**
     * 验证 has_data=false 且非只读时不附加数据前缀。
     */
    @Test
    void buildAddStorageEngineSql_shouldRespectHasDataAndReadOnly() {
        IginxConfig config = new IginxConfig();
        config.setStorageHostOverride("");
        IginxStorageEngineHelper helper = new IginxStorageEngineHelper(config);

        DataSourceConnectionConfig connection = new DataSourceConnectionConfig();
        connection.setHost("127.0.0.1");
        connection.setPort(6667);
        connection.setDatabase("default");
        connection.setUsername("root");
        connection.setPassword("root");
        connection.setHasData(false);
        connection.setReadOnly(false);

        String sql = helper.buildAddStorageEngineSql(DataSourceType.INFLUXDB, connection);

        assertTrue(sql.contains("has_data=false"));
        assertTrue(sql.contains("is_read_only=false"));
        assertFalse(sql.contains("data_prefix="));
    }

    /**
     * 验证 has_data=true 时仍按规则不附加 data_prefix 参数。
     */
    @Test
    void buildAddStorageEngineSql_shouldIncludePrefixWhenHasDataTrue() {
        IginxConfig config = new IginxConfig();
        config.setStorageHostOverride("");
        IginxStorageEngineHelper helper = new IginxStorageEngineHelper(config);

        DataSourceConnectionConfig connection = new DataSourceConnectionConfig();
        connection.setHost("127.0.0.1");
        connection.setPort(6667);
        connection.setDatabase("default");
        connection.setUsername("root");
        connection.setPassword("root");
        connection.setHasData(true);
        connection.setReadOnly(true);

        String sql = helper.buildAddStorageEngineSql(DataSourceType.INFLUXDB, connection);

        assertTrue(sql.contains("has_data=true"));
        assertTrue(sql.contains("is_read_only=true"));
        assertFalse(sql.contains("data_prefix="));
    }
}
