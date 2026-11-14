package com.xmu.rbac.structured.service;

import com.xmu.rbac.structured.dto.Actor;
import com.xmu.rbac.structured.dto.IntegrationViews.AuthoritiesVO;
import com.xmu.rbac.structured.dto.IntegrationViews.IdentityUserVO;
import com.xmu.rbac.structured.entity.Permission;
import com.xmu.rbac.structured.entity.User;
import com.xmu.rbac.structured.mapper.Store;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 受信后端身份读用例；用户身份和服务来源必须同时成立。 */
@Service
@Transactional(readOnly = true)
public class IntegrationIdentityService {
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final Store store;

    public IntegrationIdentityService(AuthorizationService authorization, AuditService audit, Store store) {
        this.authorization = authorization;
        this.audit = audit;
        this.store = store;
    }

    /** 从已验证aud计算真实API能力，不能从菜单或操作准入推导CC等细粒度权限。 */
    public AuthoritiesVO authorities(Actor actor, String serviceKey) {
        audit.trusted(serviceKey);
        AuthorizationService.Snapshot snapshot = authorization.snapshot(actor.userId(), actor.systemCode());
        TreeSet<String> values = new TreeSet<>();
        for (Long id : snapshot.permissions()) {
            Permission permission = authorization.require(Permission.class, id);
            if ("API".equals(permission.kind) && "ENABLED".equals(permission.status)) {
                values.add(permission.code);
            }
        }
        authorization.identities(snapshot).forEach(identity -> values.add("ROLE_" + identity));
        return new AuthoritiesVO(java.util.List.copyOf(values));
    }

    /** 只有拥有教师创建能力的受信管理员可查询最少身份字段。 */
    public IdentityUserVO lookup(Actor actor, String serviceKey, String subject) {
        audit.trusted(serviceKey);
        AuthorizationService.Snapshot snapshot = authorization.snapshot(actor.userId(), actor.systemCode());
        if (!authorization.identities(snapshot).contains("admin")
                || !authorization.permission(actor.userId(), actor.systemCode(), "API", "TEC")) {
            throw BizException.forbidden("FORBIDDEN");
        }
        User target = store.one(User.class, "e.subject=?1", subject);
        if (target == null || !"ACTIVE".equals(target.status)) {
            throw new BizException(404, "IDENTITY_USER_NOT_FOUND");
        }
        return new IdentityUserVO(target.subject, target.name, target.no == null ? "" : target.no);
    }
}
