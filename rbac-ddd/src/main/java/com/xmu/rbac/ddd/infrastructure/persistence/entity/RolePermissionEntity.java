package com.xmu.rbac.ddd.infrastructure.persistence.entity;

import jakarta.persistence.*;

/** 仅供持久化适配器使用，不跨越应用边界。 */
@Entity
@Table(name = "sys_role_permission")
public class RolePermissionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "permission_id")
    private Long permissionId;

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        this.id = value;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long value) {
        this.roleId = value;
    }

    public Long getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(Long value) {
        this.permissionId = value;
    }
}
