package com.xmu.rbac.ddd.domain.port;

import java.time.Instant;
import java.util.Map;

/** 密码学和 HTTP 分离，领域只依赖已验证身份声明。 */
public interface TokenProvider {
    record Principal(
            String subject, String systemCode, String jti, long tokenVersion, Instant expiresAt) {}

    String issue(String subject, String systemCode, long tokenVersion);

    Principal verify(String token);

    Map<String, Object> jwks();
}
