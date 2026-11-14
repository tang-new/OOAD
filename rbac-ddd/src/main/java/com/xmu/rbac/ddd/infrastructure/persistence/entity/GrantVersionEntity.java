package com.xmu.rbac.ddd.infrastructure.persistence.entity;

import jakarta.persistence.*;

/** 全局授权版本行，事务先取得排他锁再读取授权事实。 */
@Entity
@Table(name = "sys_grant_version")
public class GrantVersionEntity {
    @Id private Byte id;

    @Column(nullable = false)
    private Long version;

    public Byte getId() {
        return id;
    }

    public void setId(Byte id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
