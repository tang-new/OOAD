package com.xmu.rbac.ddd.application;

import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** 行锁串行化失败次数；失败结果先提交，外层再抛 401，避免回滚安全计数。 */
@Service
public class LoginAttemptApplicationService {
    private final AuthorizationRepository repository;
    private final CredentialEncoder encoder;
    private final TokenProvider tokens;
    private final AuditApplicationService audits;
    private final LoginAttemptPolicy policy = new LoginAttemptPolicy();

    public LoginAttemptApplicationService(
            AuthorizationRepository repository,
            CredentialEncoder encoder,
            TokenProvider tokens,
            AuditApplicationService audits) {
        this.repository = repository;
        this.encoder = encoder;
        this.tokens = tokens;
        this.audits = audits;
    }

    @Transactional
    public String attempt(String username, String password, String system) {
        AuthorizationRepository.LoginState state = repository.lockLogin(username);
        if (state == null) return null;
        AuthorizationRepository.Identity user = state.identity();
        Instant now = Instant.now();
        boolean active = "ACTIVE".equals(user.status());
        boolean matches =
                active
                        && !policy.locked(state.lockedUntil(), now)
                        && encoder.matches(password, user.passwordHash());
        LoginAttemptPolicy.Result result =
                policy.evaluate(state.failedCount(), state.lockedUntil(), active, matches, now);
        repository.saveLoginState(user.id(), result.failedCount(), result.lockedUntil());
        if (!result.accepted()) {
            audits.local(user, system, "LOGIN_DENIED", "登录拒绝", "DENIED", "INVALID_CREDENTIALS");
            return null;
        }
        DomainException.require(repository.systemEnabled(system), 401, "SYSTEM_DISABLED");
        String token = tokens.issue(user.subject(), system, user.tokenVersion());
        audits.local(user, system, "LOGIN", "登录", "SUCCESS", null);
        return token;
    }
}
