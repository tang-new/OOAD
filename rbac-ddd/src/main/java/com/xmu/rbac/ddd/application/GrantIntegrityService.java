package com.xmu.rbac.ddd.application;

import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;

import org.springframework.stereotype.Service;

import java.util.*;

/** 持授权锁的写事务在提交前加载已变更的事实，交领域策略保护最后管理员。 */
@Service
public class GrantIntegrityService {
    private final AuthorizationRepository grants;

    public GrantIntegrityService(AuthorizationRepository grants) {
        this.grants = grants;
    }

    public void requireAdministrator() {
        boolean enabled = grants.systemEnabled("rbac-center");
        RoleHierarchy graph = grants.hierarchy("rbac-center");
        List<Permission> permissions = grants.permissions("rbac-center");
        List<AuthorizationPolicy.Effective> users = new ArrayList<>();
        AuthorizationPolicy policy = new AuthorizationPolicy();
        for (AuthorizationRepository.Identity identity : grants.activeIdentities()) {
            if (!"/1/".equals(grants.departmentPath(identity.departmentId()))) continue;
            Set<Long> eligible = grants.eligibility(identity.departmentId()).allowedRoleIds();
            AuthorizationPolicy.Effective effective =
                    policy.calculate(
                            "rbac-center",
                            grants.assignments(identity.id(), "rbac-center"),
                            graph,
                            permissions);
            if (effective.superAdministrator()
                    && effective.effectiveRoles().stream()
                            .anyMatch(role -> role.superAdministratorCapability()
                                    && eligible.contains(role.id()))) {
                users.add(effective);
                break;
            }
        }
        new LastAdministratorPolicy().requireAvailable(enabled, users);
    }
}
