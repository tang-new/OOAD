package com.xmu.rbac.ddd.interfaces.rest.dto;

import jakarta.validation.constraints.*;

import java.util.*;

/** PATCH 使用显式存在性，区分清空可空要求与未提供字段。 */
public class ApiUpdateRequest {
    @Size(min = 1, max = 128)
    private String operationId;

    @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS")
    private String method;

    @Size(min = 1, max = 500)
    @Pattern(regexp = "^/.*")
    private String pathTemplate;

    @Size(max = 128)
    private String permissionCode;

    @Size(max = 64)
    private String requiredRole;

    private com.xmu.rbac.ddd.domain.ApiOperation.Policy accessPolicy;
    private Boolean enabled;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private final Set<String> provided = new HashSet<>();

    public Set<String> provided() {
        return Set.copyOf(provided);
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String v) {
        provided.add("operationId");
        operationId = v;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String v) {
        provided.add("method");
        method = v;
    }

    public String getPathTemplate() {
        return pathTemplate;
    }

    public void setPathTemplate(String v) {
        provided.add("pathTemplate");
        pathTemplate = v;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String v) {
        provided.add("permissionCode");
        permissionCode = v;
    }

    public String getRequiredRole() {
        return requiredRole;
    }

    public void setRequiredRole(String v) {
        provided.add("requiredRole");
        requiredRole = v;
    }

    public com.xmu.rbac.ddd.domain.ApiOperation.Policy getAccessPolicy() {
        return accessPolicy;
    }

    public void setAccessPolicy(com.xmu.rbac.ddd.domain.ApiOperation.Policy v) {
        provided.add("accessPolicy");
        accessPolicy = v;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean v) {
        provided.add("enabled");
        enabled = v;
    }
}
