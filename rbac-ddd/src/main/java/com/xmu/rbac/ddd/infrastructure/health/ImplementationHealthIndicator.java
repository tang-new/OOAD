package com.xmu.rbac.ddd.infrastructure.health;

import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

/**
 * 发布已通过本地RBAC1契约验收的实现标识，不披露环境或连接信息。
 * 整体健康仍汇总真实MySQL/Redis等探针；实现完成不等于外部部署验收。
 */
@Component("implementation")
public final class ImplementationHealthIndicator implements HealthIndicator, InfoContributor {
    private static final Map<String, Object> METADATA = Map.of(
            "backend", "ddd", "stage", "M5-RBAC1-verified", "businessReady", true);

    /** 返回本进程存活与实现标识；不会覆盖其他健康探针的结果。 */
    @Override
    public Health health() {
        return Health.up().withDetails(METADATA).build();
    }

    /** 健康详情与info使用同一份公开非敏感元数据。 */
    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetails(METADATA);
    }
}
