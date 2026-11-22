package com.xmu.rbac.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xmu.rbac.ddd.domain.DomainException;
import com.xmu.rbac.ddd.domain.User;
import com.xmu.rbac.ddd.domain.port.AuthorizationRepository;
import com.xmu.rbac.ddd.domain.port.EffectiveCache;
import com.xmu.rbac.ddd.domain.port.TokenProvider;
import com.xmu.rbac.ddd.interfaces.rest.ApiExceptionAdvice;
import com.xmu.rbac.ddd.interfaces.rest.IdentityController;
import com.xmu.rbac.ddd.interfaces.rest.RestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
                                User.Status.ACTIVE,
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
    void apiPointQueryRejectsAnOversizeOperationIdBeforeLookup() throws Exception {
        activeIdentity();
        when(repository.systemEnabled(principal.systemCode())).thenReturn(true);
        String bearer = "Bearer fixture-token";
        RestSupport rest = mock(RestSupport.class);
        when(rest.principal(bearer)).thenReturn(principal);
        var controller = new IdentityController(rest, null, application, null, null, "");
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionAdvice(null, rest, application))
                .build();
        String endpoint = "/auth/api-permissions/{operationId}";

        mvc.perform(get(endpoint, "x".repeat(129)).header("Authorization", bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(repository, never()).operation(anyString(), anyString());

        String validUnknown = "x".repeat(128);
        mvc.perform(get(endpoint, validUnknown).header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("API_OPERATION_NOT_FOUND"));
        verify(repository).operation(principal.systemCode(), validUnknown);
    }

    @Test
    void simulationReportsTargetStateWithoutRejectingTheCaller() {
        when(repository.systemEnabled("course")).thenReturn(true);
        when(repository.identity(7L)).thenReturn(new AuthorizationRepository.Identity(7, "fixture-subject", "fixture", "Fixture", "FIX-7", "unused", 1, User.Status.DISABLED, 0));
        assertThat(application.unavailableReason(7L, "course")).isEqualTo("USER_DISABLED");
        when(repository.identity(7L)).thenReturn(new AuthorizationRepository.Identity(7, "fixture-subject", "fixture", "Fixture", "FIX-7", "unused", 1, User.Status.ACTIVE, 0));
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
