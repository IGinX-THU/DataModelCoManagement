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

    /**
     * 读取表的列类型映射。
     *
     * @param connection JDBC 连接
     * @param schema schema 名称
     * @param table 表名
     * @return 列类型映射
     */
    public static Map<String, Integer> loadColumnTypes(Connection connection, String schema, String table) throws Exception {
        try (ResultSet rs = connection.getMetaData().getColumns(null, schema, table, "%")) {
            Map<String, Integer> columns = new LinkedHashMap<>();
            while (rs.next()) {
                columns.put(rs.getString("COLUMN_NAME"), rs.getInt("DATA_TYPE"));
            }
            if (columns.isEmpty()) {
                throw BizException.badRequest("表结构不存在或无字段");
            }
            return columns;
        }
    }
}
