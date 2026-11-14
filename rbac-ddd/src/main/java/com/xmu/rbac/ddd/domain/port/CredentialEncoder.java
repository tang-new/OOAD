package com.xmu.rbac.ddd.domain.port;

/** 凭据技术端口。 */
public interface CredentialEncoder {
    boolean matches(String raw, String hash);

    String encode(String raw);
}
