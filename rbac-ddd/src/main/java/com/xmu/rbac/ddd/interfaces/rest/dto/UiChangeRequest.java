package com.xmu.rbac.ddd.interfaces.rest.dto;

/** UI 变更白名单；不复制系统、资源标识、版本或操作者。 */
@cn.edu.xmu.clonefactory.CopyTo(com.xmu.rbac.ddd.application.UiChangeCommand.class)
public class UiChangeRequest {
    @com.fasterxml.jackson.annotation.JsonIgnore
    private final java.util.Set<String> provided = new java.util.HashSet<>();

    public java.util.Set<String> provided() {
        return java.util.Set.copyOf(provided);
    }

    @jakarta.validation.constraints.Positive private Long parentId;

    @jakarta.validation.constraints.Pattern(regexp = "MENU|BUTTON")
    private String type;

    @jakarta.validation.constraints.Size(min = 1, max = 128)
    private String code;

    @jakarta.validation.constraints.Size(min = 1, max = 128)
    private String name;

    @jakarta.validation.constraints.Size(max = 255)
    private String route;

    @jakarta.validation.constraints.Size(max = 64)
    private String component;

    private Integer sortOrder;

    @jakarta.validation.constraints.Pattern(regexp = "ENABLED|DISABLED")
    private String status;

    @jakarta.validation.constraints.Size(min = 1, max = 128)
    private String permissionCode;

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long value) {
        provided.add("parentId");
        parentId = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String value) {
        provided.add("type");
        type = value;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String value) {
        provided.add("code");
        code = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        provided.add("name");
        name = value;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String value) {
        provided.add("route");
        route = value;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String value) {
        provided.add("component");
        component = value;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer value) {
        provided.add("sortOrder");
        sortOrder = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        provided.add("status");
        status = value;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String value) {
        provided.add("permissionCode");
        permissionCode = value;
    }
}
