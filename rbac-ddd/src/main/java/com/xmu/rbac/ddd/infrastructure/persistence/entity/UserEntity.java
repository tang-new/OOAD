package com.xmu.rbac.ddd.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** 仅供持久化适配器使用，不跨越应用边界。 */
@Entity
@Table(name = "sys_user")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "subject")
    private String subject;

    @Column(name = "username")
    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "name")
    private String name;

    @Column(name = "no")
    private String no;

    @Column(name = "phone")
    private String phone;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "status")
    private String status;

    @Column(name = "token_version")
    private Long tokenVersion;

    @Column(name = "must_change_password")
    private Boolean mustChangePassword;

    @Column(name = "failed_login_count")
    private Integer failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "created_at")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        this.id = value;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String value) {
        this.subject = value;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String value) {
        this.username = value;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String value) {
        this.passwordHash = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        this.name = value;
    }

    public String getNo() {
        return no;
    }

    public void setNo(String value) {
        this.no = value;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String value) {
        this.phone = value;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long value) {
        this.departmentId = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        this.status = value;
    }

    public Long getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(Long value) {
        this.tokenVersion = value;
    }

    public Boolean getMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(Boolean value) {
        this.mustChangePassword = value;
    }

    public Integer getFailedLoginCount() {
        return failedLoginCount;
    }

    public void setFailedLoginCount(Integer value) {
        this.failedLoginCount = value;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant value) {
        this.lockedUntil = value;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long value) {
        this.version = value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant value) {
        this.createdAt = value;
    }
}
