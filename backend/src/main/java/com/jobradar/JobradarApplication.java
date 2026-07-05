package com.jobradar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * JobRadar 职雷 后端启动类。
 * 运行：mvn spring-boot:run （默认端口 8080，API 前缀 /api）
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class JobradarApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobradarApplication.class, args);
    }
}
