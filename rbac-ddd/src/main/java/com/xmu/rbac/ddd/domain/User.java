package com.xmu.rbac.ddd.domain;

import java.util.*;

/** 单一部门身份聚合，调动仅回收本人的不合资格指派。 */
public final class User {
    public enum Status {
        ACTIVE,
        DISABLED,
        DELETED
    }

    private final long id;
    private final String subject;
    private long departmentId;
    private Status status;
    private long tokenVersion;
    private Set<Long> roleIds;

    public User(
            long id,
            String subject,
            long departmentId,
            Status status,
            long tokenVersion,
            Set<Long> roleIds) {
        this.id = id;
        this.subject = subject;
        this.departmentId = departmentId;
        this.status = status;
        this.tokenVersion = tokenVersion;
        this.roleIds = Set.copyOf(roleIds);
    }

    public void assign(Role role, DepartmentEligibility eligibility) {
        DomainException.require(status == Status.ACTIVE, 409, "USER_DISABLED");
        DomainException.require(
                departmentId == eligibility.departmentId(), 409, "DEPARTMENT_CHANGED");
        eligibility.requireEligible(role);
        Set<Long> updated = new HashSet<>(roleIds);
        DomainException.require(updated.add(role.id()), 409, "ASSIGNMENT_EXISTS");
        roleIds = Set.copyOf(updated);
    }

    public void transfer(DepartmentEligibility destination) {
        departmentId = destination.departmentId();
        roleIds = destination.retained(roleIds);
    }

    public void changeStatus(Status status) {
        DomainException.require(this.status != Status.DELETED, 409, "USER_DELETED");
        this.status = status;
        if (status != Status.ACTIVE) tokenVersion++;
        if (status == Status.DELETED) roleIds = Set.of();
    }

    public long id() {
        return id;
    }

    public String subject() {
        return subject;
    }

    public long departmentId() {
        return departmentId;
    }

    public Status status() {
        return status;
    }

    public long tokenVersion() {
        return tokenVersion;
    }

    public Set<Long> roleIds() {
        return roleIds;
    }
}
