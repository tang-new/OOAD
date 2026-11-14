package com.xmu.rbac.structured.entity;
import jakarta.persistence.*;
import java.time.Instant;
/** 冻结数据库合同的贫血持久化记录，不承载业务规则。 */
@Entity
@Table(name = "sys_ui_resource")
public class UiResource {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long systemId;
    public Long parentId;
    public String type;
    public String code;
    public String name;
    public String route;
    public String component;
    public Integer sortOrder;
    public String status;
    public Long permissionId;
    @Version public Long version;
}
