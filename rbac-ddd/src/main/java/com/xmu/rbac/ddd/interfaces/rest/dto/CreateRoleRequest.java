package com.xmu.rbac.ddd.interfaces.rest.dto;

/** 显式边界 JavaBean，敏感身份字段不参与复制。 */
@cn.edu.xmu.clonefactory.CopyTo(com.xmu.rbac.ddd.application.RoleCommand.class)
public class CreateRoleRequest {
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 64)
    private String systemCode;

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 64)
    private String code;

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 128)
    private String name;

    @jakarta.validation.constraints.Size(max = 500)
    private String description;

    @jakarta.validation.constraints.Pattern(regexp = "student|teacher|admin")
    private String identityRole;

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

    public String getIdentityRole() {
        return identityRole;
    }

    public void setIdentityRole(String value) {
        identityRole = value;
    }
}
