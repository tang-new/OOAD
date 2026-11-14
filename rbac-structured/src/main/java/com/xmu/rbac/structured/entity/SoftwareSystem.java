package com.xmu.rbac.structured.entity;
import jakarta.persistence.*;
import java.time.Instant;
/** 冻结数据库合同的贫血持久化记录，不承载业务规则。 */
@Entity
@Table(name = "sys_system")
public class SoftwareSystem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String code;
    public String name;
    public String status;
    @Version public Long version;
}
