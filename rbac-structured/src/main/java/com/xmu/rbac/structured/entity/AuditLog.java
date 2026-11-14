package com.xmu.rbac.structured.entity;
import jakarta.persistence.*;
import java.time.Instant;
/** 冻结数据库合同的贫血持久化记录，不承载业务规则。 */
@Entity
@Table(name = "sys_audit_log")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "request_id", columnDefinition = "char(36)", length = 36)
    public String requestId;
    public String sourceSystemCode;
    public String origin;
    public Long actorId;
    public String actorSubject;
    public String actorName;
    @Column(name = "actor_roles_json", columnDefinition = "json") public String actorRolesJson;
    public String eventType;
    public String objectType;
    public String objectId;
    public String target;
    @Column(name = "before_json", columnDefinition = "json") public String beforeJson;
    @Column(name = "after_json", columnDefinition = "json") public String afterJson;
    public String result;
    public String reason;
    public String traceId;
    public String payloadHash;
    public Instant occurredAt;
}
