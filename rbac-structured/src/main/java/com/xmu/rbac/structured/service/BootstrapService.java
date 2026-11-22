package com.xmu.rbac.structured.service;
import com.xmu.rbac.structured.entity.*;
import com.xmu.rbac.structured.mapper.Store;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** 一次性受控root引导；永不重置现有密码，启动核验种子安全元数据。 */
@Service
public class BootstrapService {
    private final Store db;
    private final AuthorizationService az;
    private final PasswordEncoder passwords;
    private final String password;
    private final RouteRegistry routes;
    private final AuditService audit;
    public BootstrapService(Store db, AuthorizationService az, PasswordEncoder passwords,
        RouteRegistry routes, AuditService audit,
        @Value("${rbac.bootstrap-password:}") String password) {
        this.db = db;
        this.az = az;
        this.passwords = passwords;
        this.routes = routes;
        this.audit = audit;
        this.password = password;
    }
    /** 执行initialize操作，保持服务到持久化层的单向依赖。 */
    @Transactional
    public void initialize() {
        GrantVersion g = az.lock();
        User root = db.one(User.class, "e.username=?1", "root");
        if (root == null)
            throw new IllegalStateException("缺失root种子");
        if ("UNINITIALIZED".equals(root.passwordHash)) {
            if (password.isBlank() || password.length() < 8)
                throw new IllegalStateException("必须提供RBAC_BOOTSTRAP_PASSWORD");
            root.passwordHash = passwords.encode(password);
            g.version++;
            audit.success(null, "ROOT_INITIALIZED", "rbac-u1");
        }
        SoftwareSystem center = az.system("rbac-center");
        for (RouteRegistry.Route route : routes.all()) {
            ApiOperation op = db.one(ApiOperation.class, "e.systemId=?1 and e.operationId=?2",
                center.id, route.operationId());
            String permission = op == null || op.permissionId == null
                ? null
                : az.require(Permission.class, op.permissionId).code;
            if (op == null || !Objects.equals(op.method, route.method())
                || !Objects.equals(op.pathTemplate, route.path())
                || !Objects.equals(permission, route.permission())
                || !Objects.equals(op.requiredRole, route.requiredRole())
                || !Objects.equals(op.accessPolicy, route.policy())
                || !Boolean.TRUE.equals(op.enabled))
                throw new IllegalStateException("中心API种子与OAS不一致: " + route.operationId());
        }
        for (ApiOperation op : db.where(ApiOperation.class, "e.systemId=?1", center.id))
            if (routes.operation(op.operationId) == null)
                throw new IllegalStateException("中心存在未登记操作");
    }
}
