package com.xmu.rbac.ddd.infrastructure;

import static org.assertj.core.api.Assertions.*;

import com.xmu.rbac.ddd.domain.DomainException;
import com.xmu.rbac.ddd.domain.port.TokenProvider;
import com.xmu.rbac.ddd.infrastructure.identity.RsaTokenProvider;

import io.jsonwebtoken.Jwts;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.*;

/** 使用真实 RSA 签名测试令牌和轮换，不替代真实数据库撤销集成测试。 */
class RsaTokenProviderTest {
    @TempDir Path directory;

    private RsaTokenProvider ephemeral() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        return new RsaTokenProvider("", "", "key1", "issuer", true, environment, "");
    }

    private KeyPair pair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String pem(String name, String type, byte[] encoded) throws Exception {
        Path file = directory.resolve(name);
        Files.writeString(
                file,
                "-----BEGIN "
                        + type
                        + "-----\n"
                        + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded)
                        + "\n-----END "
                        + type
                        + "-----");
        return file.toString();
    }

    @Test
    void rs256HasSingleAudienceAndNoAuthorizationClaims() throws Exception {
        RsaTokenProvider provider = ephemeral();
        String token = provider.issue("rbac-u7", "course-project-management", 3);
        TokenProvider.Principal principal = provider.verify(token);
        assertThat(principal.subject()).isEqualTo("rbac-u7");
        assertThat(principal.systemCode()).isEqualTo("course-project-management");
        assertThat(principal.tokenVersion()).isEqualTo(3);
        String payload =
                new String(
                        Base64.getUrlDecoder().decode(token.split("[.]")[1]),
                        java.nio.charset.StandardCharsets.UTF_8);
        assertThat(payload)
                .doesNotContain("roles", "permissions")
                .contains("\"iss\":\"issuer\"", "\"iat\"", "\"exp\"", "\"jti\"");
        assertThat(provider.jwks().toString()).contains("RS256", "key1").doesNotContain("private");
    }

    @Test
    void ephemeralForbiddenInProduction() throws Exception {
        assertThatThrownBy(
                        () ->
                                new RsaTokenProvider(
                                        "", "", "k", "i", true, new MockEnvironment(), ""))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("JWT_KEY_REQUIRED");
        assertThatThrownBy(
                        () ->
                                new RsaTokenProvider(
                                        "", "", "k", "i", false, new MockEnvironment(), ""))
                .hasMessageContaining("JWT_KEY_REQUIRED");
    }

    @Test
    void rejectMalformedAndTamperedTokens() throws Exception {
        RsaTokenProvider provider = ephemeral();
        assertThatThrownBy(() -> provider.verify("broken")).hasMessageContaining("TOKEN_INVALID");
        String token = provider.issue("u", "s", 0);
        String[] pieces = token.split("[.]");
        pieces[1] = Base64.getUrlEncoder().withoutPadding().encodeToString("{}".getBytes());
        assertThatThrownBy(() -> provider.verify(String.join(".", pieces)))
                .hasMessageContaining("TOKEN_INVALID");
    }

    @Test
    void retainOldPublicKeyAndRejectUnknownKid() throws Exception {
        RsaTokenProvider provider = ephemeral();
        KeyPair old = pair();
        provider.retainVerificationKey("old", (RSAPublicKey) old.getPublic());
        String token =
                jwt(
                        old,
                        "old",
                        "issuer",
                        List.of("s"),
                        Instant.now().minusSeconds(1),
                        Instant.now().plusSeconds(300));
        assertThat(provider.verify(token).subject()).isEqualTo("u");
        assertThat(provider.jwks().toString()).contains("old");
        assertThatThrownBy(
                        () ->
                                provider.retainVerificationKey(
                                        "key1", (RSAPublicKey) old.getPublic()))
                .hasMessageContaining("ACTIVE_KEY_IMMUTABLE");
        assertThatThrownBy(
                        () ->
                                provider.verify(
                                        jwt(
                                                old,
                                                "unknown",
                                                "issuer",
                                                List.of("s"),
                                                Instant.now(),
                                                Instant.now().plusSeconds(300))))
                .hasMessageContaining("TOKEN_INVALID");
    }

    @Test
    void rejectWrongIssuerMultiAudFutureIatAndExpiration() throws Exception {
        RsaTokenProvider provider = ephemeral();
        KeyPair key = pair();
        provider.retainVerificationKey("old", (RSAPublicKey) key.getPublic());
        assertThatThrownBy(
                        () ->
                                provider.verify(
                                        jwt(
                                                key,
                                                "old",
                                                "other",
                                                List.of("s"),
                                                Instant.now(),
                                                Instant.now().plusSeconds(300))))
                .hasMessageContaining("TOKEN_INVALID");
        assertThatThrownBy(
                        () ->
                                provider.verify(
                                        jwt(
                                                key,
                                                "old",
                                                "issuer",
                                                List.of("s", "other"),
                                                Instant.now(),
                                                Instant.now().plusSeconds(300))))
                .hasMessageContaining("TOKEN_INVALID");
        assertThatThrownBy(
                        () ->
                                provider.verify(
                                        jwt(
                                                key,
                                                "old",
                                                "issuer",
                                                List.of("s"),
                                                Instant.now().plusSeconds(60),
                                                Instant.now().plusSeconds(300))))
                .hasMessageContaining("TOKEN_INVALID");
        assertThatThrownBy(
                        () ->
                                provider.verify(
                                        jwt(
                                                key,
                                                "old",
                                                "issuer",
                                                List.of("s"),
                                                Instant.now().minusSeconds(600),
                                                Instant.now().minusSeconds(1))))
                .hasMessageContaining("TOKEN_INVALID");
    }

    @Test
    void loadPemAndRotationKeys() throws Exception {
        KeyPair current = pair(), old = pair();
        String priv = pem("current-private.pem", "PRIVATE KEY", current.getPrivate().getEncoded()),
                pub = pem("current-public.pem", "PUBLIC KEY", current.getPublic().getEncoded()),
                previous = pem("old.pem", "PUBLIC KEY", old.getPublic().getEncoded());
        RsaTokenProvider provider =
                new RsaTokenProvider(
                        priv,
                        pub,
                        "current",
                        "issuer",
                        false,
                        new MockEnvironment(),
                        "old=" + previous);
        assertThat(provider.verify(provider.issue("u", "s", 1)).tokenVersion()).isEqualTo(1);
        assertThat(
                        provider.verify(
                                        jwt(
                                                old,
                                                "old",
                                                "issuer",
                                                List.of("s"),
                                                Instant.now(),
                                                Instant.now().plusSeconds(300)))
                                .subject())
                .isEqualTo("u");
    }

    @Test
    void rejectMismatchedPemPair() throws Exception {
        KeyPair a = pair(), b = pair();
        assertThatThrownBy(
                        () ->
                                new RsaTokenProvider(
                                        pem("a.pem", "PRIVATE KEY", a.getPrivate().getEncoded()),
                                        pem("b.pem", "PUBLIC KEY", b.getPublic().getEncoded()),
                                        "current",
                                        "issuer",
                                        false,
                                        new MockEnvironment(),
                                        ""))
                .hasMessageContaining("JWT_KEY_PAIR_MISMATCH");
    }

    private String jwt(
            KeyPair pair, String kid, String issuer, List<String> aud, Instant iat, Instant exp) {
        return Jwts.builder()
                .header()
                .keyId(kid)
                .and()
                .subject("u")
                .issuer(issuer)
                .audience()
                .add(aud)
                .and()
                .issuedAt(Date.from(iat))
                .expiration(Date.from(exp))
                .id(UUID.randomUUID().toString())
                .claim("tokenVersion", 0)
                .signWith(pair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }
}
