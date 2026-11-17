package com.xmu.rbac.ddd;

import static org.assertj.core.api.Assertions.*;

import com.xmu.rbac.ddd.domain.port.AuthorizationRepository;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/** 角色HTTP边界按真实输入校验；错误请求不写角色、授权版本或成功审计。 */
class RoleInputBoundaryIntegrationTest extends DddHttpTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthorizationRepository grants;

    @Test
    void roleCodeAt64CharacterLimitIsAccepted() {
        String root = login("root", "rbac-center");
        String code = UUID.randomUUID().toString().replace("-", "") + "R".repeat(32);
        long id = successful(HttpMethod.POST, "/api/v1/roles", root,
                values("systemCode", "course-project-management", "code", code, "name", "合法边界角色"))
                .path("id").asLong();
        assertThat(jdbc.queryForObject("select code from sys_role where id=?", String.class, id))
                .isEqualTo(code);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "1,1", "1000,1"})
    void permissionArraysWithinLimitRemainAcceptedAndDeduplicated(int size, int expectedLinks) {
        String root = login("root", "rbac-center");
        long id = successful(HttpMethod.POST, "/api/v1/roles", root,
                values("systemCode", "course-project-management", "code", "VALID_" + UUID.randomUUID(),
                        "name", "合法集合边界")).path("id").asLong();
        long permission = jdbc.queryForObject("select min(id) from sys_permission where system_id=2", Long.class);
        assertThat(successful(HttpMethod.PUT, "/api/v1/roles/" + id + "/permissions", root,
                values("permissionIds", Collections.nCopies(size, permission))).isNull()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from sys_role_permission where role_id=?", Long.class, id))
                .isEqualTo((long) expectedLinks);
    }

    @ParameterizedTest
    @ValueSource(strings = {"role-code", "system-code", "permission-array", "blank-name", "null-name",
            "negative-version", "role-id", "edge-id", "delete-id", "cross-system-permission"})
    void invalidRoleInputIsRejectedWithoutMutation(String scenario) {
        String root = login("root", "rbac-center");
        long target = successful(HttpMethod.POST, "/api/v1/roles", root,
                values("systemCode", "course-project-management", "code", "INPUT_" + UUID.randomUUID(),
                        "name", "输入边界角色")).path("id").asLong();
        long coursePermission = jdbc.queryForObject(
                "select min(id) from sys_permission where system_id=2", Long.class);
        long centerPermission = jdbc.queryForObject(
                "select min(id) from sys_permission where system_id=1", Long.class);
        long version = grants.version();
        long success = jdbc.queryForObject("select count(*) from sys_audit_log where result='SUCCESS'", Long.class);
        long roles = jdbc.queryForObject("select count(*) from sys_role", Long.class);
        ResponseEntity<com.fasterxml.jackson.databind.JsonNode> response = switch (scenario) {
            case "role-code" -> call(HttpMethod.POST, "/api/v1/roles", root,
                    values("systemCode", "course-project-management", "code", "R".repeat(65), "name", "过长角色码"));
            case "system-code" -> call(HttpMethod.POST, "/api/v1/roles", root,
                    values("systemCode", "S".repeat(65), "code", "C_INPUT", "name", "过长系统码"));
            case "permission-array" -> call(HttpMethod.PUT, "/api/v1/roles/" + target + "/permissions", root,
                    values("permissionIds", Collections.nCopies(1001, coursePermission)));
            case "blank-name" -> call(HttpMethod.PATCH, "/api/v1/roles/" + target, root, values("name", "   "));
            case "null-name" -> call(HttpMethod.PATCH, "/api/v1/roles/" + target, root, values("name", null));
            case "negative-version" -> call(HttpMethod.PATCH, "/api/v1/roles/" + target, root, values("version", -1));
            case "role-id" -> call(HttpMethod.GET, "/api/v1/roles/0", root, null);
            case "edge-id" -> call(HttpMethod.POST, "/api/v1/roles/0/inherits/" + target, root, null);
            case "delete-id" -> call(HttpMethod.DELETE, "/api/v1/roles/0", root, null);
            case "cross-system-permission" -> call(HttpMethod.PUT, "/api/v1/roles/" + target + "/permissions", root,
                    values("permissionIds", List.of(centerPermission)));
            default -> throw new IllegalArgumentException(scenario);
        };
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("code").asText())
                .isEqualTo(scenario.equals("cross-system-permission") ? "CROSS_SYSTEM_PERMISSION" : "VALIDATION_ERROR");
        assertThat(grants.version()).isEqualTo(version);
        assertThat(jdbc.queryForObject("select count(*) from sys_audit_log where result='SUCCESS'", Long.class))
                .isEqualTo(success);
        assertThat(jdbc.queryForObject("select count(*) from sys_role", Long.class)).isEqualTo(roles);
        assertThat(jdbc.queryForObject("select name from sys_role where id=?", String.class, target))
                .isEqualTo("输入边界角色");
        assertThat(jdbc.queryForObject("select count(*) from sys_role_permission where role_id=?", Long.class, target))
                .isZero();
    }
}
