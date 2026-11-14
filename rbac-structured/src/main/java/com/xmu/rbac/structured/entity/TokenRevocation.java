package com.xmu.rbac.structured.entity;
import jakarta.persistence.*;
import java.time.Instant;
/** 冻结数据库合同的贫血持久化记录，不承载业务规则。 */
@Entity
@Table(name = "sys_token_revocation")
public class TokenRevocation {
    @Id public String jti;
    public String subject;
    public Instant expiresAt;
    public Instant createdAt;
}
