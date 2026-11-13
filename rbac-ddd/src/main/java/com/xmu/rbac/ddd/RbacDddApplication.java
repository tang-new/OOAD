package com.xmu.rbac.ddd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DDD 后端独立启动入口；M2 仅提供进程健康与阶段信息，不代表业务就绪。
 */
@SpringBootApplication
public class RbacDddApplication {

    /**
     * 启动无需数据库、缓存或结构化后端的独立进程。
     *
     * @param args Spring Boot 启动参数，允许部署环境覆盖端口等配置
     */
    public static void main(String[] args) {
        SpringApplication.run(RbacDddApplication.class, args);
    }
}
