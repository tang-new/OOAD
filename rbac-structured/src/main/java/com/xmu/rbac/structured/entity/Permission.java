package com.xmu.rbac.structured.entity;
import jakarta.persistence.*;
import java.time.Instant;
/** 冻结数据库合同的贫血持久化记录，不承载业务规则。 */
@Entity
@Table(name = "sys_permission")
public class Permission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long systemId;
    public String code;
    public String name;
    public String kind;
    public String status;
    @Version public Long version;
}
