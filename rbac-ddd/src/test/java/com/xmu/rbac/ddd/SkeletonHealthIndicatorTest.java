package com.xmu.rbac.ddd;

import com.xmu.rbac.ddd.infrastructure.health.SkeletonHealthIndicator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.actuate.info.Info;

import static org.assertj.core.api.Assertions.assertThat;

/** 直接验证健康探针的可测试性及两个运维端点的信息一致性。 */
class SkeletonHealthIndicatorTest {

    @Test
    void skeletonHealthIsUpButNotBusinessReady() {
        Health health = new SkeletonHealthIndicator().health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                "backend", "ddd", "stage", "M2-skeleton", "businessReady", false));
    }

    @Test
    void infoContainsExactlyTheSamePublicMetadata() {
        SkeletonHealthIndicator indicator = new SkeletonHealthIndicator();
        Info.Builder builder = new Info.Builder();
        indicator.contribute(builder);
        assertThat(builder.build().getDetails()).isEqualTo(indicator.health().getDetails());
    }
}
