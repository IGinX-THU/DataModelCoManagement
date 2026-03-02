package com.xmu.iginx.assoc.modules.data.util;

import com.xmu.iginx.assoc.framework.iginx.IginxConfig;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;
import com.xmu.iginx.assoc.modules.data.enums.DataSourceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IginxStorageEngineHelperTest {

    @Test
    void buildAddStorageEngineSql_shouldUseHasDataTrueForPostgresqlWhenMountPathProvided() {
        IginxConfig iginxConfig = new IginxConfig();
        iginxConfig.setStorageHostOverride(null);
        IginxStorageEngineHelper helper = new IginxStorageEngineHelper(iginxConfig);

        DataSourceConnectionConfig config = buildPostgresConfig();
        String sql = helper.buildAddStorageEngineSql(DataSourceType.POSTGRESQL, config, "tett");

        assertTrue(sql.contains("has_data=true"));
        assertTrue(sql.contains("data_prefix=tett"));
    }

    @Test
    void buildAddStorageEngineSql_shouldUseHasDataFalseWhenMountPathBlank() {
        IginxConfig iginxConfig = new IginxConfig();
        iginxConfig.setStorageHostOverride(null);
        IginxStorageEngineHelper helper = new IginxStorageEngineHelper(iginxConfig);

        DataSourceConnectionConfig config = buildPostgresConfig();
        String sql = helper.buildAddStorageEngineSql(DataSourceType.POSTGRESQL, config, "");

        assertTrue(sql.contains("has_data=false"));
    }

    private DataSourceConnectionConfig buildPostgresConfig() {
        DataSourceConnectionConfig config = new DataSourceConnectionConfig();
        config.setHost("127.0.0.1");
        config.setPort(5433);
        config.setUsername("postgres");
        config.setPassword("postgres");
        config.setDatabase("postgres");
        config.setExtra("");
        return config;
    }
}
