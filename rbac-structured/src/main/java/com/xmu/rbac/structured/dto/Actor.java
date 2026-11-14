package com.xmu.rbac.structured.dto;
import java.time.Instant;
/** 已验证的身份，不接受HTTP输入反序列化。 */
public record Actor(Long userId, String subject, String systemCode, String jti, Instant expiresAt) {
}
