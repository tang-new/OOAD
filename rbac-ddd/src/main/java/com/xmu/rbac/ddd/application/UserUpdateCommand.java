package com.xmu.rbac.ddd.application;

/** 精确 PATCH 边界模型，标识符与 actor 不参与复制。 */
public class UserUpdateCommand {
    private String name;
    private String no;
    private String phone;
    private Long version;

    public String getName() {
        return name;
    }

    public void setName(String v) {
        name = v;
    }

    public String getNo() {
        return no;
    }

    public void setNo(String v) {
        no = v;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String v) {
        phone = v;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long v) {
        version = v;
    }
}
