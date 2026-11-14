package com.xmu.rbac.ddd.domain;

import java.util.Set;

/** 部门只约束直接角色指派，不赋予共享权限。 */
public record DepartmentEligibility(long departmentId, Set<Long> allowedRoleIds) {
    public DepartmentEligibility {
        allowedRoleIds = Set.copyOf(allowedRoleIds);
    }

    public void requireEligible(Role role) {
        DomainException.require(
                allowedRoleIds.contains(role.id()), 403, "ROLE_NOT_ASSIGNABLE_IN_DEPARTMENT");
        DomainException.require(role.enabled(), 409, "ROLE_DISABLED");
    }

    public Set<Long> retained(Set<Long> assignments) {
        java.util.HashSet<Long> retained = new java.util.HashSet<>(assignments);
        retained.retainAll(allowedRoleIds);
        return Set.copyOf(retained);
    }

    public static void requireManaged(String actorPath, String targetPath) {
        DomainException.require(targetPath.startsWith(actorPath), 403, "DEPARTMENT_OUT_OF_SCOPE");
    }
}
