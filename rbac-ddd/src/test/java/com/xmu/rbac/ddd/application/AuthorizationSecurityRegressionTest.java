package com.xmu.rbac.ddd.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** 五项安全审查中的授权回归；latch 确定性安排撤权和缓存命中时序。 */
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
