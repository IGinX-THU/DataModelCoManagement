package com.xmu.iginx.assoc.framework.iginx;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "iginx.filesystem")
public class IginxFileSystemConfig {

    private boolean enabled = true;

    private boolean autoRegister = true;

    private String host = "127.0.0.1";

    private int port = 6668;

    private String dataPrefix = "models";

    private String dir = "data";

    private String extra = "";
}
