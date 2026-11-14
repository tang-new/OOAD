package com.xmu.rbac.ddd.infrastructure.persistence.entity;

import jakarta.persistence.*;

/** 仅供持久化适配器使用，不跨越应用边界。 */
@Entity
@Table(name = "sys_ui_resource")
public class UiResourceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "system_id")
    private Long systemId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "type")
    private String type;

    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "route")
    private String route;

    @Column(name = "component")
    private String component;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "status")
    private String status;

    @Column(name = "permission_id")
    private Long permissionId;

    @Version
    @Column(name = "version")
    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        this.id = value;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long value) {
        this.systemId = value;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long value) {
        this.parentId = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String value) {
        this.type = value;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String value) {
        this.code = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        this.name = value;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String value) {
        this.route = value;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String value) {
        this.component = value;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer value) {
        this.sortOrder = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        this.status = value;
    }

    public Long getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(Long value) {
        this.permissionId = value;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long value) {
        this.version = value;
    }
}
