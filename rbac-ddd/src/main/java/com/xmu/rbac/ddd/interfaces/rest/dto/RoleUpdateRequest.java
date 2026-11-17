package com.xmu.rbac.ddd.interfaces.rest.dto;

/** 精确 PATCH 边界模型，标识符与 actor 不参与复制。 */
@cn.edu.xmu.clonefactory.CopyTo(com.xmu.rbac.ddd.application.RoleUpdateCommand.class)
public class RoleUpdateRequest {
    @com.fasterxml.jackson.annotation.JsonIgnore
    private final java.util.Set<String> provided = new java.util.HashSet<>();

    public boolean provided(String field) {
        return provided.contains(field);
    }

    @jakarta.validation.constraints.Size(min = 1, max = 128)
    @jakarta.validation.constraints.Pattern(regexp = "(?s).*\\S.*")
    private String name;

    @jakarta.validation.constraints.Size(max = 500)
    private String description;

    @jakarta.validation.constraints.PositiveOrZero
    private Long version;

    public String getName() {
        return name;
    }

    public void setName(String v) {
        provided.add("name");
        name = v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        provided.add("description");
        description = v;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long v) {
        provided.add("version");
        version = v;
    }
}
