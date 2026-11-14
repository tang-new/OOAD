package com.xmu.rbac.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xmu.rbac.ddd.domain.DomainException;
import com.xmu.rbac.ddd.domain.port.AuthorizationRepository;
import com.xmu.rbac.ddd.domain.port.EffectiveCache;
import com.xmu.rbac.ddd.domain.port.TokenProvider;

import org.junit.jupiter.api.Test;

import java.time.Instant;

/** 签名可信不代表 aud 合法；身份边界不能把未知系统变成资源404。 */
class AuthorizationTokenAudienceTest {
    private final AuthorizationRepository repository = mock(AuthorizationRepository.class);
    private final AuthorizationApplicationService application =
            new AuthorizationApplicationService(repository, mock(EffectiveCache.class));
    private final TokenProvider.Principal principal =
            new TokenProvider.Principal(
                    "rbac-u1", "unknown-aud", "fixture-jti", 1, Instant.now().plusSeconds(60));

    private void activeIdentity() {
        when(repository.identity("rbac-u1"))
                .thenReturn(
                        new AuthorizationRepository.Identity(
                                1,
                                "rbac-u1",
                                "fixture",
                                "Fixture",
                                "FIX-1",
                                "unused-fixture-hash",
                                1,
                                "ACTIVE",
                                1));
    }

    @Test
    void unknownAudienceIsUnauthorizedRatherThanNotFound() {
        activeIdentity();
        when(repository.systemEnabled("unknown-aud"))
                .thenThrow(new DomainException(404, "SYSTEM_NOT_FOUND"));
        assertThatThrownBy(() -> application.validate(principal))
                .isInstanceOfSatisfying(
                        DomainException.class, error -> assertThat(error.status()).isEqualTo(401));
    }

    @Test
    void disabledAudienceRemainsUnauthorized() {
        activeIdentity();
        when(repository.systemEnabled("unknown-aud")).thenReturn(false);
        assertThatThrownBy(() -> application.validate(principal))
                .isInstanceOfSatisfying(
                        DomainException.class, error -> assertThat(error.status()).isEqualTo(401));
    }

    @Test
    void simulationReportsTargetStateWithoutRejectingTheCaller() {
        when(repository.systemEnabled("course")).thenReturn(true);
        when(repository.identity(7L)).thenReturn(new AuthorizationRepository.Identity(7, "fixture-subject", "fixture", "Fixture", "FIX-7", "unused", 1, "DISABLED", 0));
        assertThat(application.unavailableReason(7L, "course")).isEqualTo("USER_DISABLED");
        when(repository.identity(7L)).thenReturn(new AuthorizationRepository.Identity(7, "fixture-subject", "fixture", "Fixture", "FIX-7", "unused", 1, "ACTIVE", 0));
        assertThat(application.unavailableReason(7L, "course")).isNull();
        when(repository.systemEnabled("course")).thenReturn(false);
        assertThat(application.unavailableReason(7L, "course")).isEqualTo("SYSTEM_DISABLED");
    }

    @Test
    void simulationDoesNotInventAMissingTarget() {
        when(repository.systemEnabled("course")).thenReturn(true);
        assertThatThrownBy(() -> application.unavailableReason(7L, "course"))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.status()).isEqualTo(404));
    }

    @Test
    void repositoryFailureIsNotDisguisedAsInvalidCredentials() {
        activeIdentity();
        DomainException unavailable = new DomainException(503, "STORAGE_UNAVAILABLE");
        when(repository.systemEnabled("unknown-aud")).thenThrow(unavailable);
        assertThatThrownBy(() -> application.validate(principal)).isSameAs(unavailable);
    }
}
