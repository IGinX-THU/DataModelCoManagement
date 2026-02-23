package com.xmu.iginx.assoc.modules.data.enums;

import java.util.Arrays;

public enum DataSourceType {
    INFLUXDB,
    IOTDB,
    POSTGRESQL;

    public static boolean isSupported(String value) {
        return Arrays.stream(values()).anyMatch(type -> type.name().equalsIgnoreCase(value));
    }
}
