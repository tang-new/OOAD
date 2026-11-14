package com.xmu.rbac.structured.entity;
import jakarta.persistence.*;
import java.time.Instant;
/** 冻结数据库合同的贫血持久化记录，不承载业务规则。 */
@Entity
@Table(name = "sys_grant_version")
public class GrantVersion {
    @Id @Column(columnDefinition = "tinyint") public Long id;
    public Long version;
}
