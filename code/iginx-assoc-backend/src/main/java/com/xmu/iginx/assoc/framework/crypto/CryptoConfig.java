package com.xmu.iginx.assoc.framework.crypto;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "assoc.crypto")
public class CryptoConfig {

    private String aesKey;
}
