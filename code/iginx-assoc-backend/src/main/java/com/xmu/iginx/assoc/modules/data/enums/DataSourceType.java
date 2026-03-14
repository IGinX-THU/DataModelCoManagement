package com.xmu.iginx.assoc.modules.data.enums;

import java.util.Arrays;

/**
 * 数据源类型枚举。
 */
public enum DataSourceType {
    /** InfluxDB */
    INFLUXDB,
    /** IoTDB */
    IOTDB,
    /** PostgreSQL */
    POSTGRESQL;

    /**
     * 判断是否为受支持的数据源类型。
     *
     * @param value 类型字符串
     * @return 是否支持
     */
    public static boolean isSupported(String value) {
        return Arrays.stream(values()).anyMatch(type -> type.name().equalsIgnoreCase(value));
    }
}
