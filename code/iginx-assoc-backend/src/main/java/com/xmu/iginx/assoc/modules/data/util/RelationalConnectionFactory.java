package com.xmu.iginx.assoc.modules.data.util;

import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

@Component
public class RelationalConnectionFactory {

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
