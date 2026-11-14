package com.xmu.rbac.ddd.application;

import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 认证编排，不持有签名密钥。 */
@Service
public class IdentityApplicationService {
    private final AuthorizationRepository repository;
    private final CredentialEncoder encoder;
    private final LoginAttemptApplicationService loginAttempts;
    private final AuthorizationApplicationService authorization;
    private final AuditApplicationService audits;

    public IdentityApplicationService(
            AuthorizationRepository repository,
            CredentialEncoder encoder,
            LoginAttemptApplicationService loginAttempts,
            AuthorizationApplicationService authorization,
            AuditApplicationService audits) {
        this.repository = repository;
        this.encoder = encoder;
        this.loginAttempts = loginAttempts;
        this.authorization = authorization;
        this.audits = audits;
    }

    /** 失败计数的事务已经提交后才对 HTTP 层抛出凭据错误。 */
    public String login(String username, String password, String system) {
        String token = loginAttempts.attempt(username, password, system);
        DomainException.require(token != null, 401, "INVALID_CREDENTIALS");
        return token;
    }

    @Transactional
    public void logout(TokenProvider.Principal principal) {
        AuthorizationRepository.Identity user = authorization.validate(principal);
        repository.revoke(principal.jti(), principal.subject(), principal.expiresAt());
        audits.local(user, principal.systemCode(), "LOGOUT", "退出登录", "SUCCESS", null);
    }

    @Transactional
    public void password(
            TokenProvider.Principal principal, String oldPassword, String newPassword) {
        repository.lockGrants();
        AuthorizationRepository.Identity user = authorization.validate(principal);
        DomainException.require(
                encoder.matches(oldPassword, user.passwordHash()), 401, "INVALID_CREDENTIALS");
        repository.password(user.id(), encoder.encode(newPassword));
        repository.advanceVersion();
        audits.local(user, principal.systemCode(), "PASSWORD_CHANGED", "修改密码", "SUCCESS", null);
    }
}
