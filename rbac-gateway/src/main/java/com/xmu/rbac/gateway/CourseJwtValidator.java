package com.xmu.rbac.gateway;

import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

/** 课程令牌的必填声明与单一受众验证，不接受角色或权限快照作为中心决定。 */
public final class CourseJwtValidator implements OAuth2TokenValidator<Jwt> {
    private final OAuth2TokenValidator<Jwt> standard;
    public CourseJwtValidator(String issuer) { this.standard = JwtValidators.createDefaultWithIssuer(issuer); }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        OAuth2TokenValidatorResult result = standard.validate(jwt);
        Instant now = Instant.now();
        Object kid = jwt.getHeaders().get("kid");
        if (result.hasErrors() || !List.of("course-project-management").equals(jwt.getAudience())
                || !(kid instanceof String) || ((String) kid).isBlank()
                || jwt.getSubject() == null || jwt.getSubject().isBlank()
                || jwt.getId() == null || jwt.getId().isBlank()
                || jwt.getIssuedAt() == null || jwt.getExpiresAt() == null
                || jwt.getIssuedAt().isAfter(now) || !jwt.getExpiresAt().isAfter(now)
                || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
