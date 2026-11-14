package com.xmu.rbac.ddd.infrastructure.persistence;

/** 显式 UI 边界快照；持久化标识及响应字段均由白名单控制。 */
@cn.edu.xmu.clonefactory.CopyFrom(
        com.xmu.rbac.ddd.infrastructure.persistence.entity.UiResourceEntity.class)
@cn.edu.xmu.clonefactory.CopyTo(
        com.xmu.rbac.ddd.infrastructure.persistence.entity.UiResourceEntity.class)
public class UiState {
    private Long parentId;
    private String type;
    private String code;
    private String name;
    private String route;
    private String component;
    private Integer sortOrder;
    private String status;
    private Long permissionId;

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long v) {
        parentId = v;
    }

    public String getType() {
        return type;
    }

    public void setType(String v) {
        type = v;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String v) {
        code = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        name = v;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String v) {
        route = v;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String v) {
        component = v;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer v) {
        sortOrder = v;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        status = v;
    }

    public Long getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(Long v) {
        permissionId = v;
    }
}
