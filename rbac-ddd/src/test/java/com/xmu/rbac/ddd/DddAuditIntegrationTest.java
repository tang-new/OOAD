package com.xmu.rbac.ddd;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import java.util.concurrent.*;

/** 通过真实 HTTP 和 MySQL 验证审计记录及独立事务提交。 */
class DddAuditIntegrationTest extends DddHttpTestSupport {
    @Autowired JdbcTemplate jdbc;

    private long createUser(String root, String username) {
        return successful(
                        HttpMethod.POST,
                        "/api/v1/users",
                        root,
                        values(
                                "username", username,
                                "name", "审计测试管理员",
                                "departmentId", 2,
                                "password", "Ddd-Test-Only-Password-2026!"))
                .path("id")
                .asLong();
    }

    private long courseAdminRole() {
        return jdbc.queryForObject(
                "select r.id from sys_role r join sys_system s on r.system_id=s.id where"
                        + " s.code='course-project-management' and r.code='admin'",
                Long.class);
    }

    private String courseAdmin(String root) {
        String username = "DDD_AUD_" + UUID.randomUUID().toString().replace("-", "");
        long user = createUser(root, username);
        successful(
                HttpMethod.POST,
                "/api/v1/users/" + user + "/role-assignments",
                root,
                values("roleId", courseAdminRole()));
        return login(username, "course-project-management");
    }

    private ResponseEntity<JsonNode> audit(
            HttpMethod method, String path, String token, Object body, String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) headers.set("X-Audit-Service-Key", key);
        return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private Map<String, Object> fact(String request) {
        return values(
                "requestId",
                request,
                "type",
                "score_changed",
                "objectType",
                "group",
                "objectId",
                "cpm-group-123",
                "target",
                "审计目标",
                "before",
                values("score", 60),
                "after",
                values("score", 90));
    }

    @Test
    void concurrentReplayCommitsOneRecordAndBindsSystemAndSubject() throws Exception {
        String root = login("root", "rbac-center"), actor = courseAdmin(root);
        String request = UUID.randomUUID().toString();
        Map<String, Object> body = fact(request);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<ResponseEntity<JsonNode>>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++)
                calls.add(
                        () ->
                                audit(
                                        HttpMethod.POST,
                                        "/auth/audit-logs",
                                        actor,
                                        body,
                                        "ddd-integration-service-only"));
            Set<Long> ids = new HashSet<>();
            for (Future<ResponseEntity<JsonNode>> future : pool.invokeAll(calls)) {
                ResponseEntity<JsonNode> response = future.get(180, TimeUnit.SECONDS);
                assertThat(response.getStatusCode())
                        .as("%s", response.getBody())
                        .isEqualTo(HttpStatus.CREATED);
                ids.add(response.getBody().path("id").asLong());
                assertThat(response.getBody().path("before").path("score").asInt()).isEqualTo(60);
            }
            assertThat(ids).hasSize(1);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from sys_audit_log where request_id=?",
                                    Long.class,
                                    request))
                    .isEqualTo(1);
            Map<String, Object> changed = new LinkedHashMap<>(body);
            changed.put("target", "异体");
            assertThat(
                            audit(
                                            HttpMethod.POST,
                                            "/auth/audit-logs",
                                            actor,
                                            changed,
                                            "ddd-integration-service-only")
                                    .getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            ResponseEntity<JsonNode> cross =
                    audit(
                            HttpMethod.POST,
                            "/auth/audit-logs",
                            root,
                            body,
                            "ddd-integration-service-only");
            assertThat(cross.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(cross.getBody().path("code").asText()).isEqualTo("AUDIT_DUPLICATE_CONFLICT");
            ResponseEntity<JsonNode> queried =
                    audit(
                            HttpMethod.GET,
                            "/auth/audit-logs?target=审计目标&page=1&pageSize=1",
                            actor,
                            null,
                            "ddd-integration-service-only");
            assertThat(queried.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(queried.getBody().path("items").size()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void serviceTrustAndSafePayloadAreMandatory() {
        String actor = courseAdmin(login("root", "rbac-center"));
        Map<String, Object> body = fact(UUID.randomUUID().toString());
        assertThat(audit(HttpMethod.POST, "/auth/audit-logs", actor, body, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(audit(HttpMethod.POST, "/auth/audit-logs", actor, body, "x").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        body.put("before", values("password", "must-not-be-stored"));
        assertThat(
                        audit(
                                        HttpMethod.POST,
                                        "/auth/audit-logs",
                                        actor,
                                        body,
                                        "ddd-integration-service-only")
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        body.remove("before");
        body.put("objectId", "123");
        assertThat(
                        audit(
                                        HttpMethod.POST,
                                        "/auth/audit-logs",
                                        actor,
                                        body,
                                        "ddd-integration-service-only")
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from sys_audit_log where request_id=?",
                                Long.class,
                                body.get("requestId")))
                .isZero();
    }

    @Test
    void privateAuthoritiesProjectionIsRealAndRequiresTrustedService() {
        String actor = courseAdmin(login("root", "rbac-center"));
        ResponseEntity<JsonNode> response =
                audit(
                        HttpMethod.GET,
                        "/integration/authorities",
                        actor,
                        null,
                        "ddd-integration-service-only");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> values = new ArrayList<>();
        response.getBody().path("authorities").forEach(n -> values.add(n.asText()));
        assertThat(values)
                .contains("ROLE_admin", "audit:read", "audit:write")
                .doesNotContain("admin", "SUPER_ADMIN");
        assertThat(
                        audit(HttpMethod.GET, "/integration/authorities", actor, null, null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void disabledSystemAssignmentCommitsDenialWithoutChangingGrants() {
        String root = login("root", "rbac-center");
        long user = createUser(root, "DDD_AUD_" + UUID.randomUUID().toString().replace("-", ""));
        long role = courseAdminRole();
        long system = jdbc.queryForObject(
                "select id from sys_system where code='course-project-management'", Long.class);
        String systemStatus = "/api/v1/systems/" + system + "/status";

        try {
            successful(HttpMethod.PUT, systemStatus, root, values("status", "DISABLED"));
            long lastAudit = jdbc.queryForObject("select max(id) from sys_audit_log", Long.class);
            long grantVersion = jdbc.queryForObject("select version from sys_grant_version where id=1", Long.class);

            ResponseEntity<JsonNode> response = call(
                    HttpMethod.POST, "/api/v1/users/" + user + "/role-assignments",
                    root, values("roleId", role));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().path("code").asText()).isEqualTo("ROLE_DISABLED");
            assertThat(jdbc.queryForList(
                    "select event_type, result, reason, source_system_code, actor_id from sys_audit_log where id>?",
                    lastAudit)).singleElement().satisfies(record -> {
                        assertThat(record)
                                .containsEntry("event_type", "AUTHZ_DENIED")
                                .containsEntry("result", "DENIED")
                                .containsEntry("reason", "ROLE_DISABLED")
                                .containsEntry("source_system_code", "rbac-center");
                        assertThat(((Number) record.get("actor_id")).longValue()).isEqualTo(1L);
                    });
            assertThat(jdbc.queryForObject(
                    "select count(*) from sys_user_role where user_id=? and role_id=?",
                    Long.class, user, role)).isZero();
            assertThat(jdbc.queryForObject("select version from sys_grant_version where id=1", Long.class))
                    .isEqualTo(grantVersion);
            assertThat(call(HttpMethod.GET, "/auth/me", root, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally {
            successful(HttpMethod.PUT, systemStatus, root, values("status", "ENABLED"));
        }
    }
}
