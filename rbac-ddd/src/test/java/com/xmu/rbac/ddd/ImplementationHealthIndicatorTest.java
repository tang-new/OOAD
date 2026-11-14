package com.xmu.rbac.ddd;

import static org.assertj.core.api.Assertions.assertThat;

import com.xmu.rbac.ddd.infrastructure.health.ImplementationHealthIndicator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.actuate.info.Info;

/** 直接验证健康探针的可测试性及两个运维端点的信息一致性。 */
class ImplementationHealthIndicatorTest {

    @Test
    void implementationHealthReportsVerifiedLocalFeatureSet() {
        Health health = new ImplementationHealthIndicator().health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsExactlyInAnyOrderEntriesOf(
                        java.util.Map.of(
                                "backend",
                                "ddd",
                                "stage",
                                "M5-RBAC1-verified",
                                "businessReady",
                                true));
    }

    @Test
    void infoContainsExactlyTheSamePublicMetadata() {
        ImplementationHealthIndicator indicator = new ImplementationHealthIndicator();
        Info.Builder builder = new Info.Builder();
        indicator.contribute(builder);
        assertThat(builder.build().getDetails()).isEqualTo(indicator.health().getDetails());
    }
}
