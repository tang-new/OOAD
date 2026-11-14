package com.xmu.rbac.ddd.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** 仅供持久化适配器使用，不跨越应用边界。 */
@Entity
@Table(name = "sys_user_role")
public class AssignmentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "assigned_by")
    private Long assignedBy;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        this.id = value;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long value) {
        this.userId = value;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long value) {
        this.roleId = value;
    }

    public Long getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(Long value) {
        this.assignedBy = value;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant value) {
        this.assignedAt = value;
    }
}
