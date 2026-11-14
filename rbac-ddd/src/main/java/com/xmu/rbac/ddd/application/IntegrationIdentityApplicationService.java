package com.xmu.rbac.ddd.application;

import com.xmu.rbac.ddd.domain.AuthorizationPolicy;
import com.xmu.rbac.ddd.domain.DomainException;
import com.xmu.rbac.ddd.domain.Permission;
import com.xmu.rbac.ddd.domain.port.AuthorizationRepository;
import com.xmu.rbac.ddd.domain.port.TokenProvider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.TreeSet;

/** 私有身份读用例：应用层编排领域授权与仓储端口，不接触JPA记录。 */
@Service
@Transactional(readOnly = true)
public class IntegrationIdentityApplicationService {
    private final AuthorizationApplicationService authorization;
    private final AuthorizationRepository repository;
    private final byte[] serviceDigest;
    private final boolean configured;

    public IntegrationIdentityApplicationService(
            AuthorizationApplicationService authorization,
            AuthorizationRepository repository,
            @Value("${rbac.audit-service-key:}") String key) {
        this.authorization = authorization;
        this.repository = repository;
        this.configured = !key.isBlank();
        this.serviceDigest = digest(key);
    }

    /** 用固定长度摘要进行恒定时间比较，不把用户bearer当成可信服务来源。 */
    private void requireService(String supplied) {
        DomainException.require(
                configured
                        && supplied != null
                        && MessageDigest.isEqual(serviceDigest, digest(supplied)),
                403,
                "AUDIT_SERVICE_REQUIRED");
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256不可用", error);
        }
    }

    /** 只投影当前aud的API权限与身份标记，保持菜单与AuthorityCodes分离。 */
    public AuthoritiesResult authorities(TokenProvider.Principal principal, String key) {
        requireService(key);
        AuthorizationRepository.Identity actor = authorization.validate(principal);
        AuthorizationPolicy.Effective effective =
                authorization.effective(actor.id(), principal.systemCode());
        TreeSet<String> values = new TreeSet<>();
        effective.permissions().stream()
                .filter(permission -> permission.kind() == Permission.Kind.API)
                .forEach(permission -> values.add(permission.code()));
        effective.identities().forEach(identity -> values.add("ROLE_" + identity));
        return new AuthoritiesResult(List.copyOf(values));
    }

    /** 受控最少身份字段查询，主体由稳定subject定位而非姓名猜测。 */
    public IdentityResult lookup(TokenProvider.Principal principal, String key, String subject) {
        requireService(key);
        AuthorizationRepository.Identity actor = authorization.validate(principal);
        AuthorizationPolicy.Effective effective =
                authorization.effective(actor.id(), principal.systemCode());
        DomainException.require(
                effective.identities().contains("admin")
                        && effective.has("TEC", Permission.Kind.API),
                403,
                "FORBIDDEN");
        AuthorizationRepository.Identity target = repository.identity(subject);
        DomainException.require(
                target != null && "ACTIVE".equals(target.status()), 404, "IDENTITY_USER_NOT_FOUND");
        return new IdentityResult(
                target.subject(), target.name(), target.no() == null ? "" : target.no());
    }

    /** 应用结果不携带实体或凭据，接入层只需白名单序列化。 */
    public record AuthoritiesResult(List<String> authorities) {}

    /** 最少身份结果。 */
    public record IdentityResult(String userId, String name, String no) {}
}
