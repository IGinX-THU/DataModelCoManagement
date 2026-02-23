package com.xmu.iginx.assoc.framework.iginx;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "iginx")
public class IginxConfig {
    private String host = "127.0.0.1";
    private int port = 6888;
    private String user = "root";
    private String password = "root";
    private String storageHostOverride = "host.docker.internal";
}
