package com.xmu.rbac.ddd.domain;

import java.util.Collection;

/** 最后有效超级管理员是可用性不变量，不能经任何授权变更间接破坏。 */
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
