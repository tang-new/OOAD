package com.xmu.rbac.structured.entity;
import jakarta.persistence.*;
import java.time.Instant;
/** 冻结数据库合同的贫血持久化记录，不承载业务规则。 */
@Entity
@Table(name = "sys_api_operation")
public class ApiOperation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long systemId;
    public String operationId;
    public String method;
    public String pathTemplate;
    public Long permissionId;
    public String requiredRole;
    public String accessPolicy;
    public Boolean enabled;
    @Version public Long version;
}
