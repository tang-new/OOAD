package com.xmu.rbac.ddd;

import static org.assertj.core.api.Assertions.*;

import com.xmu.rbac.ddd.domain.port.AuthorizationRepository;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/** 受影响上级的完整新闭包必须满足操作者变更前上界，拒绝不能留下授权写事实。 */
class RoleGrantUpperBoundIntegrationTest extends DddHttpTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthorizationRepository grants;

    private long role() {
        String code = "UPPER_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("insert into sys_role(system_id,code,name,status) values(2,?,?,'ENABLED')",
                code, code);
        return jdbc.queryForObject("select id from sys_role where code=?", Long.class, code);
    }

    private long permission() {
        String code = "upper:" + UUID.randomUUID();
        jdbc.update("insert into sys_permission(system_id,code,name,kind,status)"
                + " values(2,?,?,'API','ENABLED')", code, code);
        return jdbc.queryForObject("select id from sys_permission where code=?", Long.class, code);
    }

    @ParameterizedTest
    @ValueSource(strings = {"permissions", "link", "status"})
    void affectedSeniorClosureMustRemainWithinActorUpperBound(String operation) {
        long owned = role(), ancestor = role(), target = role(), junior = role();
        long low = permission(), high = permission();
        jdbc.update("insert into sys_role_permission(role_id,permission_id) values(?,?),(?,?),(?,?)",
                owned, low, ancestor, high, junior, low);
        jdbc.update("insert into sys_role_hierarchy(senior_role_id,junior_role_id) values(?,?)",
                ancestor, target);
        if (operation.equals("status")) {
            jdbc.update("update sys_role set status='DISABLED' where id=?", target);
            jdbc.update("insert into sys_role_permission(role_id,permission_id) values(?,?)", target, low);
        }
        String username = "upper_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("insert into sys_user(subject,username,password_hash,name,department_id,status,must_change_password)"
                + " select ?,?,password_hash,'上界管理员',1,'ACTIVE',false from sys_user where id=1",
                username, username);
        long actor = jdbc.queryForObject("select id from sys_user where username=?", Long.class, username);
        jdbc.update("insert into sys_user_role(user_id,role_id) values(?,2),(?,?)", actor, actor, owned);
        jdbc.update("insert into sys_department_allowed_role(department_id,role_id) values(1,?)", owned);
        jdbc.update("update sys_grant_version set version=version+1 where id=1");
        String token = login(username, "rbac-center");
        successful(HttpMethod.GET, "/api/v1/roles/" + target, token, null);
        long version = grants.version();
        long success = jdbc.queryForObject("select count(*) from sys_audit_log where result='SUCCESS'", Long.class);
        long direct = jdbc.queryForObject("select count(*) from sys_role_permission where role_id=?", Long.class, target);

        ResponseEntity<com.fasterxml.jackson.databind.JsonNode> response = switch (operation) {
            case "permissions" -> call(HttpMethod.PUT, "/api/v1/roles/" + target + "/permissions",
                    token, values("permissionIds", List.of(low)));
            case "link" -> call(HttpMethod.POST, "/api/v1/roles/" + target + "/inherits/" + junior, token, null);
            case "status" -> call(HttpMethod.PUT, "/api/v1/roles/" + target + "/status", token, values("status", "ENABLED"));
            default -> throw new IllegalArgumentException(operation);
        };
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().path("code").asText()).isEqualTo("PRIVILEGE_ESCALATION");
        assertThat(grants.version()).isEqualTo(version);
        assertThat(jdbc.queryForObject("select count(*) from sys_audit_log where result='SUCCESS'", Long.class))
                .isEqualTo(success);
        assertThat(jdbc.queryForObject("select count(*) from sys_role_permission where role_id=?", Long.class, target))
                .isEqualTo(direct);
        assertThat(jdbc.queryForObject("select count(*) from sys_role_hierarchy where senior_role_id=?", Long.class, target))
                .isZero();
        assertThat(jdbc.queryForObject("select status from sys_role where id=?", String.class, target))
                .isEqualTo(operation.equals("status") ? "DISABLED" : "ENABLED");
    }
}
