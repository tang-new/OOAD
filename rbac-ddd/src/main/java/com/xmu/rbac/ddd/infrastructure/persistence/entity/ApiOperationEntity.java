package com.xmu.rbac.ddd.infrastructure.persistence.entity;

import jakarta.persistence.*;

/** 仅供持久化适配器使用，不跨越应用边界。 */
@Entity
@Table(name = "sys_api_operation")
public class ApiOperationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "system_id")
    private Long systemId;

    @Column(name = "operation_id")
    private String operationId;

    @Column(name = "method")
    private String method;

    @Column(name = "path_template")
    private String pathTemplate;

    @Column(name = "permission_id")
    private Long permissionId;

    @Column(name = "required_role")
    private String requiredRole;

    @Column(name = "access_policy")
    private String accessPolicy;

    @Column(name = "enabled")
    private Boolean enabled;

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

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String value) {
        this.operationId = value;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String value) {
        this.method = value;
    }

    public String getPathTemplate() {
        return pathTemplate;
    }

    public void setPathTemplate(String value) {
        this.pathTemplate = value;
    }

    public Long getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(Long value) {
        this.permissionId = value;
    }

    public String getRequiredRole() {
        return requiredRole;
    }

    public void setRequiredRole(String value) {
        this.requiredRole = value;
    }

    public String getAccessPolicy() {
        return accessPolicy;
    }

    public void setAccessPolicy(String value) {
        this.accessPolicy = value;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean value) {
        this.enabled = value;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long value) {
        this.version = value;
    }
}
