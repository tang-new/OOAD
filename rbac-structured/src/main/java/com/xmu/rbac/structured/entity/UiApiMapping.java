package com.xmu.rbac.structured.entity;
import jakarta.persistence.*;
import java.time.Instant;
/** 冻结数据库合同的贫血持久化记录，不承载业务规则。 */
@Entity
@Table(name = "sys_ui_api_mapping")
public class UiApiMapping {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long uiResourceId;
    public Long apiOperationId;
}
