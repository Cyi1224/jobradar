package com.jobradar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * JobRadar 职雷 后端启动类。
 * 运行：mvn spring-boot:run （默认端口 8080，API 前缀 /api）
 */
@SpringBootApplication
public class JobradarApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobradarApplication.class, args);
    }
}
