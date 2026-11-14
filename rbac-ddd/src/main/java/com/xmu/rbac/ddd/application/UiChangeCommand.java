package com.xmu.rbac.ddd.application;

/** UI 变更白名单；不复制系统、资源标识、版本或操作者。 */
public class UiChangeCommand {
    private Long parentId;
    private String type;
    private String code;
    private String name;
    private String route;
    private String component;
    private Integer sortOrder;
    private String status;
    private String permissionCode;

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long value) {
        parentId = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String value) {
        type = value;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String value) {
        code = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        name = value;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String value) {
        route = value;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String value) {
        component = value;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer value) {
        sortOrder = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        status = value;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String value) {
        permissionCode = value;
    }
}
