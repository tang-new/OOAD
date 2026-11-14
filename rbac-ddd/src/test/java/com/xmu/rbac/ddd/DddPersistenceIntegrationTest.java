package com.xmu.rbac.ddd;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.xmu.rbac.ddd.application.*;
import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.*;

/** 用真实 MySQL 与 Redis 验证事务、指派收敛、图互斥和 HTTP 字段边界。 */
class DddPersistenceIntegrationTest extends DddHttpTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired RoleApplicationService roles;
    @Autowired AuthorizationRepository grants;
    @Autowired TokenProvider tokens;
    @Autowired PlatformTransactionManager transactions;

    private String code() {
        return "DDD_" + UUID.randomUUID().toString().replace("-", "");
    }

    private long permission(String root, String code, String kind) {
        return successful(
                        HttpMethod.POST,
                        "/api/v1/permissions",
                        root,
                        values(
                                "systemCode",
                                "course-project-management",
                                "code",
                                code,
                                "name",
                                "测试权限",
                                "kind",
                                kind))
                .path("id")
                .asLong();
    }

    private long role(String root, String code) {
        return successful(
                        HttpMethod.POST,
                        "/api/v1/roles",
                        root,
                        values(
                                "systemCode",
                                "course-project-management",
                                "code",
                                code,
                                "name",
                                "测试角色"))
                .path("id")
                .asLong();
    }

    @Test
    void uiPatchClearsNullableFieldsAndKeepsOtherFields() {
        String root = login("root", "rbac-center");
        String code = code();
        permission(root, code, "UI");
        long id =
                successful(
                                HttpMethod.POST,
                                "/api/v1/systems/course-project-management/ui-resources",
                                root,
                                values(
                                        "type",
                                        "MENU",
                                        "code",
                                        code,
                                        "name",
                                        "菜单",
                                        "permissionCode",
                                        code,
                                        "route",
                                        "/before",
                                        "component",
                                        "Before"))
                        .path("id")
                        .asLong();
        JsonNode patched =
                successful(
                        HttpMethod.PATCH,
                        "/api/v1/ui-resources/" + id,
                        root,
                        values("route", null, "component", null, "parentId", null));
        assertThat(patched.path("route").isNull()).isTrue();
        assertThat(patched.path("component").isNull()).isTrue();
        assertThat(patched.path("name").asText()).isEqualTo("菜单");
        assertThat(
                        jdbc.queryForObject(
                                "select route from sys_ui_resource where id=?", String.class, id))
                .isNull();
        assertThat(
                        call(
                                        HttpMethod.PATCH,
                                        "/api/v1/ui-resources/" + id,
                                        root,
                                        values("name", null))
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(
                        call(
                                        HttpMethod.PATCH,
                                        "/api/v1/ui-resources/" + id,
                                        root,
                                        values("systemCode", "other"))
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        successful(HttpMethod.DELETE, "/api/v1/ui-resources/" + id, root, null);
    }

    @Test
    void appFailureRollsBackRoleVersionAndSuccessAudit() {
        String root = login("root", "rbac-center");
        TokenProvider.Principal principal = tokens.verify(root);
        String code = code();
        long before = grants.version();
        RoleCommand command = new RoleCommand();
        command.setSystemCode("course-project-management");
        command.setCode(code);
        command.setName("事务回滚角色");
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(transactions)
                                        .execute(
                                                status -> {
                                                    roles.create(principal, command);
                                                    throw new DomainException(
                                                            409, "INJECTED_FAILURE");
                                                }))
                .hasMessageContaining("INJECTED_FAILURE");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from sys_role where code=?", Long.class, code))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from sys_audit_log where target=? and"
                                        + " result='SUCCESS'",
                                Long.class,
                                code))
                .isZero();
        assertThat(grants.version()).isEqualTo(before);
    }

    @Test
    void concurrentInverseEdgesCannotCommitCycle() throws Exception {
        String root = login("root", "rbac-center");
        long a = role(root, code()), b = role(root, code());
        TokenProvider.Principal principal = tokens.verify(root);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<Boolean>> calls =
                    List.of(
                            () -> {
                                start.await();
                                try {
                                    roles.link(principal, a, b, true);
                                    return true;
                                } catch (DomainException e) {
                                    assertThat(e.code()).isEqualTo("ROLE_CYCLE");
                                    return false;
                                }
                            },
                            () -> {
                                start.await();
                                try {
                                    roles.link(principal, b, a, true);
                                    return true;
                                } catch (DomainException e) {
                                    assertThat(e.code()).isEqualTo("ROLE_CYCLE");
                                    return false;
                                }
                            });
            Future<Boolean> first = pool.submit(calls.get(0)), second = pool.submit(calls.get(1));
            start.countDown();
            assertThat(List.of(first.get(180, TimeUnit.SECONDS), second.get(180, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from sys_role_hierarchy where senior_role_id"
                                            + " in (?,?)",
                                    Long.class,
                                    a,
                                    b))
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void transferReclaimsIneligibleAssignmentWithoutChangingSharedRole() {
        String root = login("root", "rbac-center");
        String permissionCode = code();
        long permission = permission(root, permissionCode, "UI");
        long role = role(root, code());
        successful(
                HttpMethod.PUT,
                "/api/v1/roles/" + role + "/permissions",
                root,
                values("permissionIds", List.of(permission)));
        List<Long> allowed =
                new ArrayList<>(
                        jdbc.queryForList(
                                "select role_id from sys_department_allowed_role where"
                                        + " department_id=2",
                                Long.class));
        allowed.add(role);
        successful(
                HttpMethod.PUT,
                "/api/v1/departments/2/assignable-roles",
                root,
                values("roleIds", allowed));
        String username = code();
        long user =
                successful(
                                HttpMethod.POST,
                                "/api/v1/users",
                                root,
                                values(
                                        "username",
                                        username,
                                        "name",
                                        "调动测试",
                                        "departmentId",
                                        2,
                                        "password",
                                        "Ddd-Test-Only-Password-2026!"))
                        .path("id")
                        .asLong();
        successful(
                HttpMethod.POST,
                "/api/v1/users/" + user + "/role-assignments",
                root,
                values("roleId", role));
        String bearer = login(username, "course-project-management");
        assertThat(
                        call(HttpMethod.GET, "/auth/permissions/" + permissionCode, bearer, null)
                                .getBody()
                                .path("allowed")
                                .asBoolean())
                .isTrue();
        successful(
                HttpMethod.PUT,
                "/api/v1/roles/" + role + "/status",
                root,
                values("status", "DISABLED"));
        assertThat(
                        call(HttpMethod.GET, "/auth/permissions/" + permissionCode, bearer, null)
                                .getBody()
                                .path("allowed")
                                .asBoolean())
                .isFalse();
        successful(
                HttpMethod.PUT,
                "/api/v1/roles/" + role + "/status",
                root,
                values("status", "ENABLED"));
        assertThat(
                        call(HttpMethod.GET, "/auth/permissions/" + permissionCode, bearer, null)
                                .getBody()
                                .path("allowed")
                                .asBoolean())
                .isTrue();
        successful(
                HttpMethod.PUT,
                "/api/v1/users/" + user + "/department",
                root,
                values("departmentId", 3));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from sys_user_role where user_id=? and role_id=?",
                                Long.class,
                                user,
                                role))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from sys_role_permission where role_id=? and"
                                        + " permission_id=?",
                                Long.class,
                                role,
                                permission))
                .isEqualTo(1);
        assertThat(
                        call(HttpMethod.GET, "/auth/permissions/" + permissionCode, bearer, null)
                                .getBody()
                                .path("allowed")
                                .asBoolean())
                .isFalse();
    }

    @Test
    void independentJpaPagingIsStableAndRejectsInvalidBounds() {
        String root = login("root", "rbac-center");
        JsonNode page = successful(HttpMethod.GET, "/api/v1/users?page=1&pageSize=1", root, null);
        assertThat(page.path("items").size()).isEqualTo(1);
        assertThat(page.path("items").get(0).path("id").asLong()).isEqualTo(1);
        assertThat(page.path("total").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(call(HttpMethod.GET, "/api/v1/users?page=0", root, null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(
                        call(
                                        HttpMethod.GET,
                                        "/api/v1/roles?systemCode=rbac-center&pageSize=101",
                                        root,
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
