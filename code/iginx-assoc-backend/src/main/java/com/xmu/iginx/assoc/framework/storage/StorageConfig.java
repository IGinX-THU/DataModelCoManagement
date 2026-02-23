package com.xmu.iginx.assoc.framework.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "assoc.storage")
public class StorageConfig {

    private String modelRoot = "storage";

    private String dataRoot = "storage/data";
}
