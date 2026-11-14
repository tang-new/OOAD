package com.xmu.rbac.ddd.domain;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;

/** 五次失败、九百秒锁定以及成功重置的纯领域边界。 */
class LoginAttemptPolicyTest {
    private final LoginAttemptPolicy policy = new LoginAttemptPolicy();
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void fifthFailureStartsExact900SecondLock() {
        LoginAttemptPolicy.Result result = policy.evaluate(4, null, true, false, now);
        assertThat(result.accepted()).isFalse();
        assertThat(result.failedCount()).isEqualTo(5);
        assertThat(result.lockedUntil()).isEqualTo(now.plusSeconds(900));
    }

    @Test
    void lockedAccountCannotAuthenticateEvenWithCorrectPassword() {
        LoginAttemptPolicy.Result result = policy.evaluate(5, now.plusSeconds(1), true, true, now);
        assertThat(result.accepted()).isFalse();
        assertThat(result.failedCount()).isEqualTo(5);
    }

    @Test
    void expirationBoundaryRestartsFailureCount() {
        LoginAttemptPolicy.Result result = policy.evaluate(5, now, true, false, now);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.lockedUntil()).isNull();
        assertThat(result.accepted()).isFalse();
    }

    @Test
    void correctCredentialsClearBothPersistentFields() {
        LoginAttemptPolicy.Result result = policy.evaluate(4, null, true, true, now);
        assertThat(result.accepted()).isTrue();
        assertThat(result.failedCount()).isZero();
        assertThat(result.lockedUntil()).isNull();
    }

    @Test
    void inactiveUserNeverAuthenticates() {
        assertThat(policy.evaluate(0, null, false, true, now).accepted()).isFalse();
    }
}
