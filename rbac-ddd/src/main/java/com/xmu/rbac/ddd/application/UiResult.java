package com.xmu.rbac.ddd.application;

/** 显式 UI 边界快照；持久化标识及响应字段均由白名单控制。 */
public class UiResult {
    private Long id;
    private String systemCode;
    private Long parentId;
    private String type;
    private String code;
    private String name;
    private String route;
    private String component;
    private Integer sortOrder;
    private String status;
    private String permissionCode;

    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        id = v;
    }

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String v) {
        systemCode = v;
    }

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

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String v) {
        permissionCode = v;
    }

    public static UiResult from(com.xmu.rbac.ddd.domain.port.ResourceRepository.Ui ui) {
        UiResult result = new UiResult();
        result.setId(ui.id());
        result.setSystemCode(ui.systemCode());
        result.setParentId(ui.parentId());
        result.setType(ui.type());
        result.setCode(ui.code());
        result.setName(ui.name());
        result.setRoute(ui.route());
        result.setComponent(ui.component());
        result.setSortOrder(ui.sortOrder());
        result.setStatus(ui.status());
        result.setPermissionCode(ui.permissionCode());
        return result;
    }
}
