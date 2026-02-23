package com.xmu.iginx.assoc.modules.data.util;

import com.xmu.iginx.assoc.common.exception.BizException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JDBC 元数据工具类，用于统一读取表结构信息。
 */
public final class JdbcMetadataUtils {

    private JdbcMetadataUtils() {
    }

    public static Map<String, Integer> loadColumnTypes(Connection connection, String schema, String table) throws Exception {
        try (ResultSet rs = connection.getMetaData().getColumns(null, schema, table, "%")) {
            Map<String, Integer> columns = new LinkedHashMap<>();
            while (rs.next()) {
                columns.put(rs.getString("COLUMN_NAME"), rs.getInt("DATA_TYPE"));
            }
            if (columns.isEmpty()) {
                throw BizException.badRequest("琛ㄧ粨鏋勪笉瀛樺湪鎴栨棤瀛楁");
            }
            return columns;
        }
    }
}
