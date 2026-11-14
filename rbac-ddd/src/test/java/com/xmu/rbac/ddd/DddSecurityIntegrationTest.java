package com.xmu.rbac.ddd;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.xmu.rbac.ddd.application.*;
import com.xmu.rbac.ddd.domain.port.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.*;

/** 五项安全审查的真实 MySQL/HTTP 回归，不用 mock 掩盖持久计数或权限写事务。 */
class DddSecurityIntegrationTest extends DddHttpTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthorizationRepository grants;
    @Autowired AuditApplicationService audits;
    @Autowired CredentialEncoder encoder;
    @Autowired TokenProvider tokens;
    @Autowired PlatformTransactionManager transactions;

    private String code() {
        return "SEC_" + UUID.randomUUID().toString().replace("-", "");
    }

    private long user(String root, String username, long department) {
        return successful(
                        HttpMethod.POST,
                        "/api/v1/users",
                        root,
                        values(
                                "username",
                                username,
                                "name",
                                "安全回归",
                                "departmentId",
                                department,
                                "password",
                                "Ddd-Test-Only-Password-2026!"))
                .path("id")
                .asLong();
    }

    private void allow(String root, long department, long role) {
        List<Long> ids =
                new ArrayList<>(
                        jdbc.queryForList(
                                "select role_id from sys_department_allowed_role where"
                                        + " department_id=?",
                                Long.class,
                                department));
        ids.add(role);
        successful(
                HttpMethod.PUT,
                "/api/v1/departments/" + department + "/assignable-roles",
                root,
                values("roleIds", ids));
    }

    @Test
    void nonSuperAdminCannotEmptyEditGrantOrInheritSpecialCapability() {
        String root = login("root", "rbac-center");
        long special =
                jdbc.queryForObject(
                        "select r.id from sys_role r join sys_system s on r.system_id=s.id where"
                                + " s.code='rbac-center' and r.code='SUPER_ADMIN'",
                        Long.class);
        List<Long> original =
                jdbc.queryForList(
                        "select permission_id from sys_role_permission where role_id=?",
                        Long.class,
                        special);
        long deputy =
                successful(
                                HttpMethod.POST,
                                "/api/v1/roles",
                                root,
                                values(
                                        "systemCode",
                                        "rbac-center",
                                        "code",
                                        code(),
                                        "name",
                                        "非超级管理员"))
                        .path("id")
                        .asLong();
        List<Long> all = new ArrayList<>();
        successful(
                        HttpMethod.GET,
                        "/api/v1/permissions?systemCode=rbac-center&kind=API&pageSize=100",
                        root,
                        null)
                .path("items")
                .forEach(p -> all.add(p.path("id").asLong()));
        successful(
                HttpMethod.PUT,
                "/api/v1/roles/" + deputy + "/permissions",
                root,
                values("permissionIds", all));
        allow(root, 1, deputy);
        String username = code();
        long id = user(root, username, 1);
        successful(
                HttpMethod.POST,
                "/api/v1/users/" + id + "/role-assignments",
                root,
                values("roleId", deputy));
        String actor = login(username, "rbac-center");
        try {
            successful(
                    HttpMethod.PUT,
                    "/api/v1/roles/" + special + "/permissions",
                    root,
                    values("permissionIds", List.of()));
            assertThat(
                            call(
                                            HttpMethod.PUT,
                                            "/api/v1/roles/" + special + "/permissions",
                                            actor,
                                            values("permissionIds", List.of()))
                                    .getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(
                            call(
                                            HttpMethod.POST,
                                            "/api/v1/users/" + id + "/role-assignments",
                                            actor,
                                            values("roleId", special))
                                    .getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(
                            call(
                                            HttpMethod.POST,
                                            "/api/v1/roles/" + deputy + "/inherits/" + special,
                                            actor,
                                            null)
                                    .getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(
                            call(
                                            HttpMethod.PATCH,
                                            "/api/v1/roles/" + special,
                                            actor,
                                            values("name", "篡改"))
                                    .getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(
                            call(
                                            HttpMethod.PUT,
                                            "/api/v1/roles/" + special + "/status",
                                            actor,
                                            values("status", "DISABLED"))
                                    .getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from sys_user_role where user_id=? and"
                                            + " role_id=?",
                                    Long.class,
                                    id,
                                    special))
                    .isZero();
        } finally {
            successful(
                    HttpMethod.PUT,
                    "/api/v1/roles/" + special + "/permissions",
                    root,
                    values("permissionIds", original));
        }
    }

    @Test
    void inactiveTargetCannotUseAuthenticatedApiThroughCheckOrExplain() {
        String root = login("root", "rbac-center");
        String username = code();
        long id = user(root, username, 2);
        String operation = code();
        successful(
                HttpMethod.POST,
                "/api/v1/systems/course-project-management/api-operations",
                root,
                values(
                        "operationId",
                        operation,
                        "method",
                        "GET",
                        "pathTemplate",
                        "/security/" + operation,
                        "accessPolicy",
                        "AUTHENTICATED",
                        "enabled",
                        true));
        Map<String, Object> input =
                values(
                        "systemCode",
                        "course-project-management",
                        "userId",
                        id,
                        "kind",
                        "API",
                        "operationId",
                        operation);
        assertThat(
                        successful(HttpMethod.POST, "/api/v1/authorization/check", root, input)
                                .path("allowed")
                                .asBoolean())
                .isTrue();
        for (String status : List.of("DISABLED", "DELETED")) {
            successful(
                    HttpMethod.PUT,
                    "/api/v1/users/" + id + "/status",
                    root,
                    values("status", status));
            assertThat(
                            successful(HttpMethod.POST, "/api/v1/authorization/check", root, input)
                                    .path("allowed")
                                    .asBoolean())
                    .isFalse();
            assertThat(
                            successful(
                                            HttpMethod.POST,
                                            "/api/v1/authorization/explain",
                                            root,
                                            input)
                                    .path("allowed")
                                    .asBoolean())
                    .isFalse();
        }
    }

    @Test
    void concurrentFailuresPersistLockAndCorrectLoginCannotBypassItAfterServiceReconstruction()
            throws Exception {
        String root = login("root", "rbac-center");
        String username = code();
        long id = user(root, username, 2);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<ResponseEntity<JsonNode>>> requests = new ArrayList<>();
            for (int i = 0; i < 5; i++)
                requests.add(
                        () ->
                                http.postForEntity(
                                        "/auth/login",
                                        values(
                                                "username",
                                                username,
                                                "password",
                                                "wrong-test-password",
                                                "systems",
                                                List.of("course-project-management")),
                                        JsonNode.class));
            for (Future<ResponseEntity<JsonNode>> response : pool.invokeAll(requests))
                assertThat(response.get(180, TimeUnit.SECONDS).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED);
        } finally {
            pool.shutdownNow();
        }
        assertThat(
                        jdbc.queryForObject(
                                "select failed_login_count from sys_user where id=?",
                                Integer.class,
                                id))
                .isEqualTo(5);
        assertThat(
                        jdbc.queryForObject(
                                "select timestampdiff(second,utc_timestamp(),locked_until) from"
                                        + " sys_user where id=?",
                                Long.class,
                                id))
                .isBetween(600L, 900L);
        LoginAttemptApplicationService reconstructed =
                new LoginAttemptApplicationService(grants, encoder, tokens, audits);
        String token =
                new TransactionTemplate(transactions)
                        .execute(
                                s ->
                                        reconstructed.attempt(
                                                username,
                                                "Ddd-Test-Only-Password-2026!",
                                                "course-project-management"));
        assertThat(token).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "select failed_login_count from sys_user where id=?",
                                Integer.class,
                                id))
                .isEqualTo(5);
        jdbc.update(
                "update sys_user set locked_until=utc_timestamp()-interval 1 second where id=?",
                id);
        assertThat(login(username, "course-project-management")).isNotBlank();
        assertThat(
                        jdbc.queryForObject(
                                "select failed_login_count from sys_user where id=?",
                                Integer.class,
                                id))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select locked_until from sys_user where id=?",
                                java.sql.Timestamp.class,
                                id))
                .isNull();
    }

    @Test
    void internalSuccessAndDeniedKeepOriginalIdentityRolesAfterRevocation() throws Exception {
        String root = login("root", "rbac-center");
        String username = code();
        long id = user(root, username, 2);
        long teacher =
                jdbc.queryForObject(
                        "select r.id from sys_role r join sys_system s on s.id=r.system_id where"
                                + " s.code='course-project-management' and r.code='teacher'",
                        Long.class);
        long assignment =
                successful(
                                HttpMethod.POST,
                                "/api/v1/users/" + id + "/role-assignments",
                                root,
                                values("roleId", teacher))
                        .path("id")
                        .asLong();
        AuthorizationRepository.Identity actor = grants.username(username);
        String target = code();
        long success =
                new TransactionTemplate(transactions)
                        .execute(
                                s ->
                                        audits.local(
                                                actor,
                                                "course-project-management",
                                                "SNAPSHOT",
                                                target,
                                                "SUCCESS",
                                                null))
                        .id();
        audits.denied(actor, "course-project-management", target, "PERMISSION_DENIED");
        List<String> before =
                jdbc.queryForList(
                        "select actor_roles_json from sys_audit_log where target=? order by id",
                        String.class,
                        target);
        assertThat(before).hasSize(2);
        for (String roles : before) assertThat(json.readTree(roles).toString()).contains("teacher");
        successful(
                HttpMethod.DELETE,
                "/api/v1/users/" + id + "/role-assignments/" + assignment,
                root,
                null);
        assertThat(
                        jdbc.queryForList(
                                "select actor_roles_json from sys_audit_log where target=? order by"
                                        + " id",
                                String.class,
                                target))
                .containsExactlyElementsOf(before);
        assertThat(
                        jdbc.queryForObject(
                                "select result from sys_audit_log where id=?",
                                String.class,
                                success))
                .isEqualTo("SUCCESS");
    }
}
