package com.xmu.rbac.structured;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.rbac.structured.entity.ApiOperation;
import com.xmu.rbac.structured.entity.GrantVersion;
import com.xmu.rbac.structured.entity.Permission;
import com.xmu.rbac.structured.entity.Role;
import com.xmu.rbac.structured.entity.RolePermission;
import com.xmu.rbac.structured.entity.SoftwareSystem;
import com.xmu.rbac.structured.entity.User;
import com.xmu.rbac.structured.entity.UserRole;
import com.xmu.rbac.structured.mapper.Store;
import com.xmu.rbac.structured.service.AuthorizationService;
import com.xmu.rbac.structured.service.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;

class AuthorizationConsistencyTest {
    private final Store store = mock(Store.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ObjectMapper json = new ObjectMapper();
    private final AuthorizationService authorization = new AuthorizationService(store, redis, json);

    @BeforeEach
    void setUp() {
        SoftwareSystem system = new SoftwareSystem();
        system.id = 2L;
        system.status = "ENABLED";
        when(store.one(SoftwareSystem.class, "e.code=?1", "course")).thenReturn(system);
        when(store.get(SoftwareSystem.class, 2L)).thenReturn(system);
        when(store.get(GrantVersion.class, 1L)).thenReturn(version(7), version(8));
    }

    @Test
    void apiReadsOperationAgainWhenVersionChanges() {
        when(store.get(User.class, 42L)).thenReturn(user("ACTIVE"));
        ApiOperation enabled = new ApiOperation();
        enabled.enabled = true;
        enabled.accessPolicy = "PUBLIC";
        ApiOperation disabled = new ApiOperation();
        disabled.enabled = false;
        when(store.one(ApiOperation.class, "e.systemId=?1 and e.operationId=?2", 2L, "operation"))
            .thenReturn(enabled, disabled);

        assertThat(authorization.api(42L, "course", "operation")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void snapshotChecksUserStatusAgainWhenVersionChanges() {
        when(store.get(User.class, 42L)).thenReturn(user("ACTIVE"), user("DISABLED"));
        when(redis.opsForValue()).thenReturn(mock(ValueOperations.class));

        assertThatExceptionOfType(BizException.class)
            .isThrownBy(() -> authorization.snapshot(42L, "course"))
            .satisfies(error -> assertThat(error.status).isEqualTo(401));
    }

    @Test
    void enabledCenterOperationRecognizesSuperAdminWithoutExplicitPermission() {
        SoftwareSystem center = new SoftwareSystem();
        center.id = 1L;
        center.status = "ENABLED";
        Role role = role(3L, center.id, "SUPER_ADMIN");
        role.builtin = true;
        ApiOperation operation = new ApiOperation();
        operation.enabled = true;
        operation.accessPolicy = "PROTECTED";
        operation.permissionId = 4L;
        when(store.one(SoftwareSystem.class, "e.code=?1", "rbac-center")).thenReturn(center);
        when(store.get(SoftwareSystem.class, center.id)).thenReturn(center);
        when(store.get(User.class, 42L)).thenReturn(user("ACTIVE"));
        when(store.get(Role.class, role.id)).thenReturn(role);
        when(store.where(UserRole.class, "e.userId=?1", 42L))
            .thenReturn(List.of(assignment(role.id)));
        when(store.where(Role.class, "e.systemId=?1", center.id)).thenReturn(List.of(role));
        when(store.one(ApiOperation.class, "e.systemId=?1 and e.operationId=?2", center.id,
                      "listSystems")).thenReturn(operation);

        assertThat(authorization.api(42L, "rbac-center", "listSystems")).isTrue();

        operation.enabled = false;
        assertThat(authorization.api(42L, "rbac-center", "listSystems")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void reloadsUntypedCachedSourcesForPermissionsWithTheSameCode() throws Exception {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        Role uiRole = role(10L, 2L, "UI_ROLE");
        Role apiRole = role(20L, 2L, "API_ROLE");
        when(store.get(User.class, 42L)).thenReturn(user("ACTIVE"));
        when(store.get(GrantVersion.class, 1L)).thenReturn(version(7));
        when(store.get(Role.class, 10L)).thenReturn(uiRole);
        when(store.get(Role.class, 20L)).thenReturn(apiRole);
        when(store.where(UserRole.class, "e.userId=?1", 42L))
            .thenReturn(List.of(assignment(10L), assignment(20L)));
        when(store.where(Role.class, "e.systemId=?1", 2L)).thenReturn(List.of(uiRole, apiRole));
        when(store.where(Permission.class, "e.systemId=?1 and e.status=?2", 2L, "ENABLED"))
            .thenReturn(List.of(permission(100L, "UI"), permission(200L, "API")));
        when(store.all(RolePermission.class))
            .thenReturn(List.of(grant(10L, 100L), grant(20L, 200L)));
        var previous = new AuthorizationService.Snapshot(List.of(10L, 20L), List.of(10L, 20L),
            List.of(100L, 200L), List.of(Map.of("permissionCode", "shared", "roleCode", "UI_ROLE",
                "inherited", false, "path", List.of("UI_ROLE"))));
        when(values.get("structured:authz:course:42:7")).thenReturn(json.writeValueAsString(previous));

        var result = authorization.snapshot(42L, "course");

        assertThat(result.sources()).containsExactly(
            Map.of("permissionCode", "shared", "permissionKind", "UI", "roleCode", "UI_ROLE",
                "inherited", false, "path", List.of("UI_ROLE")),
            Map.of("permissionCode", "shared", "permissionKind", "API", "roleCode", "API_ROLE",
                "inherited", false, "path", List.of("API_ROLE")));
    }

    @Test
    void apiRetriesMissingOperationWhenVersionChanges() {
        when(store.get(User.class, 42L)).thenReturn(user("ACTIVE"));
        ApiOperation operation = new ApiOperation();
        operation.enabled = true;
        operation.accessPolicy = "PUBLIC";
        when(store.one(ApiOperation.class, "e.systemId=?1 and e.operationId=?2", 2L, "operation"))
            .thenReturn(null, operation);

        assertThat(authorization.api(42L, "course", "operation")).isTrue();
        assertThatExceptionOfType(BizException.class)
            .isThrownBy(() -> authorization.api(42L, "course", "missing"))
            .satisfies(error -> {
                assertThat(error.status).isEqualTo(404);
                assertThat(error.code).isEqualTo("API_OPERATION_NOT_FOUND");
            });
    }

    private static GrantVersion version(long number) {
        GrantVersion version = new GrantVersion();
        version.version = number;
        return version;
    }

    private static User user(String status) {
        User user = new User();
        user.status = status;
        return user;
    }

    private static Role role(long id, long systemId, String code) {
        Role role = new Role();
        role.id = id;
        role.systemId = systemId;
        role.status = "ENABLED";
        role.code = code;
        return role;
    }

    private static UserRole assignment(long roleId) {
        UserRole assignment = new UserRole();
        assignment.roleId = roleId;
        return assignment;
    }

    private static Permission permission(long id, String kind) {
        Permission permission = new Permission();
        permission.id = id;
        permission.code = "shared";
        permission.kind = kind;
        return permission;
    }

    private static RolePermission grant(long roleId, long permissionId) {
        RolePermission grant = new RolePermission();
        grant.roleId = roleId;
        grant.permissionId = permissionId;
        return grant;
    }
}
