package com.xmu.rbac.structured.entity;
import jakarta.persistence.*;
import java.time.Instant;
/** 冻结数据库合同的贫血持久化记录，不承载业务规则。 */
@Entity
@Table(name = "sys_user")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String subject;
    public String username;
    public String passwordHash;
    public String name;
    public String no;
    public String phone;
    public Long departmentId;
    public String status;
    public Long tokenVersion;
    public Boolean mustChangePassword;
    public Integer failedLoginCount;
    public Instant lockedUntil;
    @Version public Long version;
    public Instant createdAt;
}
