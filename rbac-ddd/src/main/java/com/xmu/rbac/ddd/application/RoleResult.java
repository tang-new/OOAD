package com.xmu.rbac.ddd.application;

/** 显式边界 JavaBean，敏感身份字段不参与复制。 */
public class RoleResult {
    private Long id;
    private String systemCode;
    private String code;
    private String name;
    private String description;
    private String status;
    private Boolean builtin;
    private String identityRole;
    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        id = value;
    }

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String value) {
        systemCode = value;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String value) {
        description = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        status = value;
    }

    public Boolean getBuiltin() {
        return builtin;
    }

    public void setBuiltin(Boolean value) {
        builtin = value;
    }

    public String getIdentityRole() {
        return identityRole;
    }

    public void setIdentityRole(String value) {
        identityRole = value;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long value) {
        version = value;
    }
}
