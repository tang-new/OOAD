package com.xmu.rbac.ddd.infrastructure.persistence.entity;

import jakarta.persistence.*;

/** 仅供持久化适配器使用，不跨越应用边界。 */
@Entity
@Table(name = "sys_role_hierarchy")
public class HierarchyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "senior_role_id")
    private Long seniorRoleId;

    @Column(name = "junior_role_id")
    private Long juniorRoleId;

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        this.id = value;
    }

    public Long getSeniorRoleId() {
        return seniorRoleId;
    }

    public void setSeniorRoleId(Long value) {
        this.seniorRoleId = value;
    }

    public Long getJuniorRoleId() {
        return juniorRoleId;
    }

    public void setJuniorRoleId(Long value) {
        this.juniorRoleId = value;
    }
}
