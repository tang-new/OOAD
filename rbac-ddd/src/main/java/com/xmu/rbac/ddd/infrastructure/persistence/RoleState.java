package com.xmu.rbac.ddd.infrastructure.persistence;

import cn.edu.xmu.clonefactory.CopyFrom;
import cn.edu.xmu.clonefactory.CopyTo;

import com.xmu.rbac.ddd.infrastructure.persistence.entity.RoleEntity;

/** 安全持久化快照；系统标识与数据库身份显式处理。 */
@CopyFrom(RoleEntity.class)
@CopyTo(RoleEntity.class)
public class RoleState {
    private String code;
    private String name;
    private String description;
    private String status;
    private Boolean builtin;
    private String identityRole;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        description = v;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        status = v;
    }

    public Boolean getBuiltin() {
        return builtin;
    }

    public void setBuiltin(Boolean v) {
        builtin = v;
    }

    public String getIdentityRole() {
        return identityRole;
    }

    public void setIdentityRole(String v) {
        identityRole = v;
    }
}
