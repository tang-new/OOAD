package com.xmu.rbac.ddd.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** 仅供持久化适配器使用，不跨越应用边界。 */
@Entity
@Table(name = "sys_audit_log")
public class AuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "request_id", length = 36, columnDefinition = "char(36)")
    private String requestId;

    @Column(name = "source_system_code")
    private String sourceSystemCode;

    @Column(name = "origin")
    private String origin;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_subject")
    private String actorSubject;

    @Column(name = "actor_name")
    private String actorName;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "actor_roles_json")
    private String actorRolesJson;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "object_type")
    private String objectType;

    @Column(name = "object_id")
    private String objectId;

    @Column(name = "target")
    private String target;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "before_json")
    private String beforeJson;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "after_json")
    private String afterJson;

    @Column(name = "result")
    private String result;

    @Column(name = "reason")
    private String reason;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "payload_hash")
    private String payloadHash;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        this.id = value;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String value) {
        this.requestId = value;
    }

    public String getSourceSystemCode() {
        return sourceSystemCode;
    }

    public void setSourceSystemCode(String value) {
        this.sourceSystemCode = value;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String value) {
        this.origin = value;
    }

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long value) {
        this.actorId = value;
    }

    public String getActorSubject() {
        return actorSubject;
    }

    public void setActorSubject(String value) {
        this.actorSubject = value;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String value) {
        this.actorName = value;
    }

    public String getActorRolesJson() {
        return actorRolesJson;
    }

    public void setActorRolesJson(String value) {
        this.actorRolesJson = value;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String value) {
        this.eventType = value;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String value) {
        this.objectType = value;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String value) {
        this.objectId = value;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String value) {
        this.target = value;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public void setBeforeJson(String value) {
        this.beforeJson = value;
    }

    public String getAfterJson() {
        return afterJson;
    }

    public void setAfterJson(String value) {
        this.afterJson = value;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String value) {
        this.result = value;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String value) {
        this.reason = value;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String value) {
        this.traceId = value;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String value) {
        this.payloadHash = value;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant value) {
        this.occurredAt = value;
    }
}
