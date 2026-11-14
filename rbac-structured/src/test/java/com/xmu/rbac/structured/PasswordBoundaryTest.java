package com.xmu.rbac.structured;

import static org.assertj.core.api.Assertions.assertThat;

import com.xmu.rbac.structured.config.WebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 口令长度边界及旧哈希兼容回归，防止72字节截断造成认证等价。 */
class PasswordBoundaryTest {
    @Test
    void differentPasswordsAfterByte72RemainDifferent() {
        PasswordEncoder encoder = WebConfig.passwords();
        String first = "x".repeat(255) + "A";
        String second = "x".repeat(255) + "B";
        String encoded = encoder.encode(first);
        assertThat(encoder.matches(first, encoded)).isTrue();
        assertThat(encoder.matches(second, encoded)).isFalse();
        assertThat(encoded.length()).isLessThanOrEqualTo(100);
    }

    @Test
    void existingBcryptHashesAreNotResetOrReinterpreted() {
        String password = "SyntheticLegacyPassword123!";
        String legacy = new BCryptPasswordEncoder().encode(password);
        assertThat(WebConfig.passwords().matches(password, legacy)).isTrue();
        assertThat(WebConfig.passwords().matches("different", legacy)).isFalse();
    }
}
