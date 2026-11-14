package com.xmu.rbac.ddd.infrastructure.persistence.entity;

import jakarta.persistence.*;

/** 仅供持久化适配器使用，不跨越应用边界。 */
@Entity
@Table(name = "sys_ui_api_mapping")
public class MappingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ui_resource_id")
    private Long uiResourceId;

    @Column(name = "api_operation_id")
    private Long apiOperationId;

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        this.id = value;
    }

    public Long getUiResourceId() {
        return uiResourceId;
    }

    public void setUiResourceId(Long value) {
        this.uiResourceId = value;
    }

    public Long getApiOperationId() {
        return apiOperationId;
    }

    public void setApiOperationId(Long value) {
        this.apiOperationId = value;
    }
}
