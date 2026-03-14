package com.xmu.iginx.assoc.modules.data.util;

import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * 关系型数据库连接工厂。
 */
@Component
public class RelationalConnectionFactory {

    /**
     * 打开 PostgreSQL 连接。
     *
     * @param config 连接配置
     * @return JDBC 连接
     */
    public Connection openPostgresConnection(DataSourceConnectionConfig config) {
        String url = String.format("jdbc:postgresql://%s:%d/%s",
            config.getHost(), config.getPort(), config.getDatabase());
        Properties props = new Properties();
        props.setProperty("user", config.getUsername());
        props.setProperty("password", config.getPassword());
        try {
            return DriverManager.getConnection(url, props);
        } catch (SQLException ex) {
            throw BizException.badRequest("无法连接到关系型数据库: " + ex.getMessage());
        }
    }
}
