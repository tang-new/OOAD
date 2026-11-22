package com.xmu.rbac.ddd.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;
import com.xmu.rbac.ddd.interfaces.rest.AuthorizationController;
import com.xmu.rbac.ddd.interfaces.rest.AuthorizationController.Check;
import com.xmu.rbac.ddd.interfaces.rest.RestSupport;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** 授权回归；latch 确定性安排撤权和缓存命中时序。 */
class AuthorizationSecurityRegressionTest {
    private AuthorizationRepository.Identity user(User.Status status) {
        return new AuthorizationRepository.Identity(
                7, "u7", "u7", "用户", "7", "unused", 1, status, 0);
    }

    private Role role() {
        return new Role(2, "s", "viewer", "查看者", null, true, false, null, 0, Set.of(1L));
    }

    private Permission permission() {
        return new Permission(1, "s", "view", "查看", Permission.Kind.API, true);
    }

    private AuthorizationRepository repository() {
        AuthorizationRepository r = mock(AuthorizationRepository.class);
        when(r.systemEnabled("s")).thenReturn(true);
        when(r.identity(7L)).thenReturn(user(User.Status.ACTIVE));
        when(r.assignments(7, "s")).thenReturn(Set.of(2L));
        when(r.hierarchy("s")).thenReturn(new RoleHierarchy(List.of(role()), Set.of()));
        when(r.permissions("s")).thenReturn(List.of(permission()));
        return r;
    }

    private AuthorizationPolicy.Effective allowed() {
        return new AuthorizationPolicy()
                .calculate(
                        "s",
                        Set.of(2L),
                        new RoleHierarchy(List.of(role()), Set.of()),
                        List.of(permission()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"permission", "operationId"})
    void suppliedAuthorizationSelectorHasLengthBetweenOneAnd128(String field) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validateValue(Check.class, field, "")).hasSize(1);
            assertThat(validator.validateValue(Check.class, field, "x".repeat(129))).hasSize(1);
            assertThat(validator.validateValue(Check.class, field, "x")).isEmpty();
            assertThat(validator.validateValue(Check.class, field, "x".repeat(128))).isEmpty();
            assertThat(validator.validateValue(Check.class, field, null)).isEmpty();
        }
    }

    @Test
    void emptyPermissionSuperAdminStillCannotBeGranted() {
        Role special =
                new Role(
                        1,
                        "rbac-center",
                        "SUPER_ADMIN",
                        "超级管理员",
                        null,
                        true,
                        true,
                        null,
                        0,
                        Set.of());
        AuthorizationPolicy.Effective proposed =
                new AuthorizationPolicy()
                        .calculate(
                                "rbac-center",
                                Set.of(1L),
                                new RoleHierarchy(List.of(special), Set.of()),
                                List.of());
        AuthorizationPolicy.Effective actor =
                new AuthorizationPolicy.Effective(List.of(), List.of(), List.of(), List.of());
        assertThatThrownBy(() -> new AuthorizationPolicy().requireWithin(actor, proposed, false))
                .hasMessageContaining("GRANT_EXCEEDS_ACTOR");
    }

    @Test
    void inactiveAndDeletedTargetsNeverKeepEffectivePermissions() {
        AuthorizationRepository r = repository();
        AuthorizationApplicationService service =
                new AuthorizationApplicationService(r, mock(EffectiveCache.class));
        for (User.Status status : List.of(User.Status.DISABLED, User.Status.DELETED)) {
            when(r.identity(7L)).thenReturn(user(status));
            assertThat(service.effective(7, "s").permissions()).isEmpty();
            when(r.operation("s", "publicOp"))
                    .thenReturn(
                            new ApiOperation(
                                    1,
                                    "s",
                                    "publicOp",
                                    "GET",
                                    "/public",
                                    null,
                                    null,
                                    ApiOperation.Policy.PUBLIC,
                                    true));
            assertThat(service.api(7, "s", "publicOp")).isFalse();
        }
    }

    @Test
    void administratorCanViewEmptyPermissionsForDisabledTargetSystem() {
        var principal = new TokenProvider.Principal("u7", "rbac-center", "jti", 0, null);
        DirectoryRepository directory = mock(DirectoryRepository.class);
        AuthorizationRepository grants = mock(AuthorizationRepository.class);
        AuthorizationApplicationService authorization = mock(AuthorizationApplicationService.class);
        var service = new DirectoryApplicationService(directory, grants, authorization, null, null, null);
        when(authorization.require(principal, "rbac:user:view", false))
                .thenReturn(user(User.Status.ACTIVE));
        when(directory.user(8, false))
                .thenReturn(new DirectoryRepository.Profile(
                        8L, "u8", "u8", "用户", "8", null, 1L, "总部", "ACTIVE", 0L, null, 0L));
        when(grants.departmentPath(1)).thenReturn("/1/");
        when(directory.department(1))
                .thenReturn(new DirectoryRepository.Department(
                        1L, null, "root", "总部", "/1/", "ENABLED", 0L));
        when(authorization.effective(8, "disabled")).thenThrow(new DomainException(401, "SYSTEM_DISABLED"));
        when(authorization.effective(8, "unknown")).thenThrow(new DomainException(404, "SYSTEM_NOT_FOUND"));

        assertThat(service.effective(principal, 8, "disabled"))
                .isEqualTo(new AuthorizationPolicy.Effective(List.of(), List.of(), List.of(), List.of()));
        assertThatThrownBy(() -> service.effective(principal, 8, "unknown"))
                .isInstanceOf(DomainException.class).hasMessage("SYSTEM_NOT_FOUND");

        when(authorization.require(principal, "rbac:user:view", false))
                .thenThrow(new DomainException(401, "SYSTEM_DISABLED"));
        assertThatThrownBy(() -> service.effective(principal, 8, "disabled"))
                .isInstanceOf(DomainException.class).hasMessage("SYSTEM_DISABLED");
    }

    @Test
    void revocationWhileBuildingSnapshotMustRetryInsteadOfReturningOldGrant() throws Exception {
        AuthorizationRepository r = repository();
        AtomicLong version = new AtomicLong(1);
        when(r.version()).thenAnswer(invocation -> version.get());
        CountDownLatch loaded = new CountDownLatch(1), revoked = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        when(r.permissions("s"))
                .thenAnswer(
                        invocation -> {
                            if (loads.getAndIncrement() == 0) {
                                loaded.countDown();
                                assertThat(revoked.await(5, TimeUnit.SECONDS)).isTrue();
                                return List.of(permission());
                            }
                            return List.of();
                        });
        AuthorizationApplicationService service =
                new AuthorizationApplicationService(r, mock(EffectiveCache.class));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<AuthorizationPolicy.Effective> result =
                    worker.submit(() -> service.effective(7, "s"));
            assertThat(loaded.await(5, TimeUnit.SECONDS)).isTrue();
            version.incrementAndGet();
            revoked.countDown();
            assertThat(result.get(5, TimeUnit.SECONDS).permissions()).isEmpty();
        } finally {
            revoked.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    void revocationDuringCacheHitMustNotReturnHit() throws Exception {
        AuthorizationRepository r = repository();
        AtomicLong version = new AtomicLong(1);
        when(r.version()).thenAnswer(invocation -> version.get());
        when(r.permissions("s")).thenReturn(List.of());
        EffectiveCache cache = mock(EffectiveCache.class);
        CountDownLatch hit = new CountDownLatch(1), revoked = new CountDownLatch(1);
        when(cache.get("s", 7, 1))
                .thenAnswer(
                        invocation -> {
                            hit.countDown();
                            assertThat(revoked.await(5, TimeUnit.SECONDS)).isTrue();
                            return allowed();
                        });
        AuthorizationApplicationService service = new AuthorizationApplicationService(r, cache);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<AuthorizationPolicy.Effective> result =
                    worker.submit(() -> service.effective(7, "s"));
            assertThat(hit.await(5, TimeUnit.SECONDS)).isTrue();
            version.incrementAndGet();
            revoked.countDown();
            assertThat(result.get(5, TimeUnit.SECONDS).permissions()).isEmpty();
        } finally {
            revoked.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    void continuouslyChangingAuthorityVersionFailsClosed() {
        AuthorizationRepository r = repository();
        AtomicLong version = new AtomicLong();
        when(r.version()).thenAnswer(invocation -> version.incrementAndGet());
        assertThatThrownBy(
                        () ->
                                new AuthorizationApplicationService(r, mock(EffectiveCache.class))
                                        .effective(7, "s"))
                .hasMessageContaining("AUTHORIZATION_UNSTABLE");
    }

    @Test
    void operationPolicyChangeDuringResolutionCannotReuseEarlierPublicPolicy() {
        AuthorizationRepository r = repository();
        AtomicLong version = new AtomicLong(1);
        when(r.version()).thenAnswer(i -> version.get());
        when(r.permissions("s")).thenReturn(List.of());
        AtomicInteger calls = new AtomicInteger();
        when(r.operation("s", "op"))
                .thenAnswer(
                        i -> {
                            if (calls.getAndIncrement() == 0) {
                                version.set(2);
                                return new ApiOperation(
                                        1,
                                        "s",
                                        "op",
                                        "GET",
                                        "/op",
                                        null,
                                        null,
                                        ApiOperation.Policy.PUBLIC,
                                        true);
                            }
                            return new ApiOperation(
                                    1,
                                    "s",
                                    "op",
                                    "GET",
                                    "/op",
                                    "view",
                                    null,
                                    ApiOperation.Policy.PROTECTED,
                                    true);
                        });
        assertThat(
                        new AuthorizationApplicationService(r, mock(EffectiveCache.class))
                                .api(7, "s", "op"))
                .isFalse();
    }

    @Test
    void apiExplanationUsesTheSameVersionForDecisionAndPermissions() {
        AuthorizationRepository r = repository();
        AtomicLong version = new AtomicLong(1);
        when(r.version()).thenAnswer(i -> version.get());
        when(r.permissions("s"))
                .thenAnswer(i -> version.get() == 1 ? List.of(permission()) : List.of());
        ApiOperation operation =
                new ApiOperation(
                        1, "s", "op", "GET", "/op", "view", null,
                        ApiOperation.Policy.PROTECTED, true);
        when(r.operation("s", "op"))
                .thenAnswer(
                        i -> {
                            version.set(2);
                            return operation;
                        });
        AuthorizationApplicationService service =
                new AuthorizationApplicationService(r, mock(EffectiveCache.class));

        AuthorizationApplicationService.ApiExplanation result = service.explainApi(7, "s", "op");

        assertThat(result.allowed()).isFalse();
        assertThat(result.unavailableReason()).isNull();
        assertThat(result.effective().permissions()).isEmpty();
    }

    @Test
    void apiExplanationRetriesWhenSystemIsDisabledAfterTheStatusRead() {
        AuthorizationRepository r = repository();
        AtomicLong version = new AtomicLong(1);
        when(r.version()).thenAnswer(i -> version.get());
        when(r.systemEnabled("s"))
                .thenReturn(true)
                .thenAnswer(
                        i -> {
                            version.set(2);
                            return false;
                        });
        AuthorizationApplicationService service =
                new AuthorizationApplicationService(r, mock(EffectiveCache.class));

        AuthorizationApplicationService.ApiExplanation result = service.explainApi(7, "s", "op");

        assertThat(result.allowed()).isFalse();
        assertThat(result.unavailableReason()).isEqualTo("SYSTEM_DISABLED");
        assertThat(result.effective().permissions()).isEmpty();
        verify(r, never()).operation("s", "op");
    }

    @Test
    void uiExplanationReturnsDenialWhenTargetSystemIsDisabledAfterTheStatusRead() {
        String bearer = "Bearer fixture";
        var principal = new TokenProvider.Principal("u7", "rbac-center", "jti", 0, null);
        RestSupport rest = mock(RestSupport.class);
        DirectoryApplicationService directory = mock(DirectoryApplicationService.class);
        AuthorizationApplicationService authorization = mock(AuthorizationApplicationService.class);
        var controller = new AuthorizationController(rest, authorization, directory, null);
        var request = new Check("s", 8L, Permission.Kind.UI, "view", null);
        when(rest.principal(bearer)).thenReturn(principal);
        when(authorization.require(principal, "rbac:authz:invoke", false))
                .thenReturn(user(User.Status.ACTIVE));
        when(authorization.unavailableReason(8L, "s")).thenReturn(null);
        when(authorization.effective(8L, "s")).thenThrow(new DomainException(401, "SYSTEM_DISABLED"));

        var response = (Map<?, ?>) controller.explain(bearer, request);
        var result = (Map<?, ?>) response.get("data");

        assertThat(response.get("code")).isEqualTo("OK");
        assertThat(result.get("allowed")).isEqualTo(false);
        assertThat(result.get("reason")).isEqualTo("SYSTEM_DISABLED");
        assertThat(result.get("effective")).isEqualTo(Map.of(
                "directRoles", List.of(), "effectiveRoles", List.of(),
                "permissions", List.of(), "sources", List.of()));

        when(authorization.require(principal, "rbac:authz:invoke", false))
                .thenThrow(new DomainException(401, "SYSTEM_DISABLED"));
        assertThatThrownBy(() -> controller.explain(bearer, request))
                .isInstanceOf(DomainException.class).hasMessage("SYSTEM_DISABLED");
        verify(authorization).effective(8L, "s");
    }

    @Test
    void inheritedAndDisabledSpecialCapabilityCannotHideBehindEmptyPermissions() {
        Role special =
                new Role(
                        1,
                        "rbac-center",
                        "SUPER_ADMIN",
                        "超级管理员",
                        null,
                        true,
                        true,
                        null,
                        0,
                        Set.of());
        Role ordinary =
                new Role(
                        2, "rbac-center", "ordinary", "普通角色", null, true, false, null, 0, Set.of());
        RoleHierarchy graph =
                new RoleHierarchy(List.of(special, ordinary), Set.of(new RoleHierarchy.Edge(2, 1)));
        AuthorizationPolicy.Effective inherited =
                new AuthorizationPolicy().calculate("rbac-center", Set.of(2L), graph, List.of());
        assertThatThrownBy(
                        () ->
                                new AuthorizationPolicy()
                                        .requireWithin(
                                                new AuthorizationPolicy.Effective(
                                                        List.of(), List.of(), List.of(), List.of()),
                                                inherited,
                                                false))
                .hasMessageContaining("GRANT_EXCEEDS_ACTOR");
        special.changeStatus(false);
        assertThatThrownBy(() -> graph.requireGrantable(Set.of(2L), false))
                .hasMessageContaining("GRANT_EXCEEDS_ACTOR");
        assertThatThrownBy(() -> special.requireManageable(false))
                .hasMessageContaining("SUPER_ADMIN_PROTECTED");
        graph.requireGrantable(Set.of(2L), true);
    }

    @Test
    void missingUserAndUnknownOperationFailClosed() {
        AuthorizationRepository r = repository();
        when(r.identity(7L)).thenReturn(null);
        AuthorizationApplicationService service =
                new AuthorizationApplicationService(r, mock(EffectiveCache.class));
        assertThatThrownBy(() -> service.effective(7, "s")).hasMessageContaining("USER_NOT_FOUND");
        assertThatThrownBy(() -> service.api(7, "s", "missing"))
                .hasMessageContaining("API_OPERATION_NOT_FOUND");
    }
}
