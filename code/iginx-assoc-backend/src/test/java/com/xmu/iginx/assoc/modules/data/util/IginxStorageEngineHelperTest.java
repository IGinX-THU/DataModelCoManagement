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
     * 未配置前缀时不应拼接 schema_prefix/data_prefix。
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
        assertFalse(sql.contains("schema_prefix="));
        assertFalse(sql.contains("data_prefix="));
    }

    /**
     * 配置 schemaPrefix/dataPrefix 后应拼接到 extra 参数中。
     */
    @Test
    void buildAddStorageEngineSql_shouldIncludePrefixWhenConfigured() {
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
        connection.setSchemaPrefix("demo_schema");
        connection.setDataPrefix("demo_data");

        String sql = helper.buildAddStorageEngineSql(DataSourceType.INFLUXDB, connection);

        assertTrue(sql.contains("has_data=true"));
        assertTrue(sql.contains("is_read_only=true"));
        assertTrue(sql.contains("schema_prefix=demo_schema"));
        assertTrue(sql.contains("data_prefix=demo_data"));
    }

    /**
     * 卸载语句应按用户手册格式拼接四元组，空前缀使用空字符串。
     */
    @Test
    void buildRemoveStorageEngineSql_shouldUseSchemaAndDataPrefixTuple() {
        IginxConfig config = new IginxConfig();
        config.setStorageHostOverride("host.docker.internal");
        IginxStorageEngineHelper helper = new IginxStorageEngineHelper(config);

        DataSourceConnectionConfig connection = new DataSourceConnectionConfig();
        connection.setHost("127.0.0.1");
        connection.setPort(6667);
        connection.setSchemaPrefix("schema_a");
        connection.setDataPrefix("");

        String sql = helper.buildRemoveStorageEngineSql(connection);

        assertTrue(sql.startsWith("REMOVE STORAGEENGINE (\"host.docker.internal\", 6667, "));
        assertTrue(sql.contains(", \"schema_a\", \"\""));
        assertTrue(sql.endsWith(");"));
    }
}
