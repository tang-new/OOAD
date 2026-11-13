package com.xmu.rbac.ddd.infrastructure.health;

import java.util.Map;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

/**
 * 发布骨架进程的阶段信息，不探测尚未引入的数据库或缓存。
 * UP 仅表示本进程可用；businessReady=false 明确声明业务尚未实现。
 * 整体健康状态仍由 Actuator 汇总真实探针（例如磁盘空间），不强制覆盖为 UP。
 */
@Component("skeleton")
public final class SkeletonHealthIndicator implements HealthIndicator, InfoContributor {

    private static final Map<String, Object> METADATA = Map.of(
            "backend", "ddd",
            "stage", "M2-skeleton",
            "businessReady", false);

    /**
     * 返回无需外部资源的骨架存活状态及非敏感阶段标识。
     *
     * @return 骨架自身的 UP 状态，不代表业务就绪
     */
    @Override
    public Health health() {
        return Health.up().withDetails(METADATA).build();
    }

    /**
     * 向 info 端点提供与健康详情一致的阶段信息，不披露环境变量或连接信息。
     *
     * @param builder Actuator 提供的信息构建器
     */
    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetails(METADATA);
    }
}
