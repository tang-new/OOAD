package com.xmu.rbac.ddd.domain;

import java.util.Collection;

/** 输入限于具有根部门管理资格及角色准入的用户，保护最后可用超级管理员。 */
public final class LastAdministratorPolicy {
    public void requireAvailable(
            boolean systemEnabled, Collection<AuthorizationPolicy.Effective> activeUsers) {
        DomainException.require(
                systemEnabled
                        && activeUsers.stream()
                                .anyMatch(AuthorizationPolicy.Effective::superAdministrator),
                409,
                "LAST_SUPER_ADMIN_PROTECTED");
    }
}
