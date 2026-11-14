package com.xmu.rbac.ddd.interfaces.rest.dto;

/** 精确 PATCH 边界模型，标识符与 actor 不参与复制。 */
@cn.edu.xmu.clonefactory.CopyTo(com.xmu.rbac.ddd.application.UserUpdateCommand.class)
public class UserUpdateRequest {
    @com.fasterxml.jackson.annotation.JsonIgnore
    private final java.util.Set<String> provided = new java.util.HashSet<>();

    public boolean provided(String field) {
        return provided.contains(field);
    }

    @jakarta.validation.constraints.Size(max = 128)
    private String name;

    @jakarta.validation.constraints.Size(max = 64)
    private String no;

    @jakarta.validation.constraints.Size(max = 32)
    private String phone;

    private Long version;

    public String getName() {
        return name;
    }

    public void setName(String v) {
        provided.add("name");
        name = v;
    }

    public String getNo() {
        return no;
    }

    public void setNo(String v) {
        provided.add("no");
        no = v;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String v) {
        provided.add("phone");
        phone = v;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long v) {
        provided.add("version");
        version = v;
    }
}
