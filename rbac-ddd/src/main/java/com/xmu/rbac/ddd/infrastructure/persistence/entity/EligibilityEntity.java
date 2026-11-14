package com.xmu.rbac.ddd.infrastructure.persistence.entity;

import jakarta.persistence.*;

/** 仅供持久化适配器使用，不跨越应用边界。 */
@Entity
@Table(name = "sys_department_allowed_role")
public class EligibilityEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "role_id")
    private Long roleId;

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        this.id = value;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long value) {
        this.departmentId = value;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long value) {
        this.roleId = value;
    }
}
