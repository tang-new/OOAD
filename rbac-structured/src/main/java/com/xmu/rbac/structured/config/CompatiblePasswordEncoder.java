package com.xmu.rbac.structured.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 支持合同允许的长口令，避免BCrypt的72字节截断导致两个不同口令等价。
 * 新口令先做SHA-256再BCrypt，并保存可识别前缀；现存无前缀BCrypt哈希仍原样校验，不重置密码。
 */
public final class CompatiblePasswordEncoder implements PasswordEncoder {
    private static final String PREFIX = "{bcrypt-sha256}";
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    /** 编码结果长度75，符合冻结password_hash VARCHAR(100)。 */
    @Override
    public String encode(CharSequence rawPassword) {
        return PREFIX + bcrypt.encode(digest(rawPassword));
    }

    /** 仅新格式使用预哈希，兼容已初始化的原BCrypt哈希。 */
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        try {
            if (encodedPassword.startsWith(PREFIX)) {
                return bcrypt.matches(
                    digest(rawPassword), encodedPassword.substring(PREFIX.length()));
            }
            return bcrypt.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException malformedHash) {
            return false;
        }
    }

    private String digest(CharSequence rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("口令不能为空");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                rawPassword.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256不可用", unavailable);
        }
    }
}
