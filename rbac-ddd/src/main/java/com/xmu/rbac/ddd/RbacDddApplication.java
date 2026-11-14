package com.xmu.rbac.ddd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** DDD 后端独立启动入口；通过真实数据库与缓存访问基础设施。 */
@SpringBootApplication(
        exclude =
                org.springframework.boot.autoconfigure.security.servlet
                        .UserDetailsServiceAutoConfiguration.class)
public class RbacDddApplication {

    /**
     * 启动独立领域后端，不依赖结构化实现。
     *
     * @param args Spring Boot 启动参数，允许部署环境覆盖端口等配置
     */
    public static void main(String[] args) {
        SpringApplication.run(RbacDddApplication.class, args);
    }
}
