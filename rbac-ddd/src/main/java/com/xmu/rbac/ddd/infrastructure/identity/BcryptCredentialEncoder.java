package com.xmu.rbac.ddd.infrastructure.identity;

import com.xmu.rbac.ddd.domain.port.CredentialEncoder;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** 密码只在基础设施哈希，不进入日志或 VO。 */
@Component
public class BcryptCredentialEncoder implements CredentialEncoder {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public boolean matches(String raw, String hash) {
        return hash != null && !hash.equals("UNINITIALIZED") && encoder.matches(raw, hash);
    }

    public String encode(String raw) {
        return encoder.encode(raw);
    }
}
