package com.xmu.iginx.assoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用启动入口，负责初始化 Spring Boot 容器并开启异步/定时能力。
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class IginxAssocApplication {

    /**
     * 启动应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(IginxAssocApplication.class, args);
    }

}
