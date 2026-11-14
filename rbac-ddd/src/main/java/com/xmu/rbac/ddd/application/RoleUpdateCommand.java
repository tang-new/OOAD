package com.xmu.rbac.ddd.application;

/** 精确 PATCH 边界模型，标识符与 actor 不参与复制。 */
public class RoleUpdateCommand {
    private String name;
    private String description;
    private Long version;

    public String getName() {
        return name;
    }

    public void setName(String v) {
        name = v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        description = v;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long v) {
        version = v;
    }
}
