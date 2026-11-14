package com.xmu.rbac.ddd.infrastructure.identity;

import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;

import io.jsonwebtoken.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.time.*;
import java.util.*;

/** RS256 身份声明，不嵌入角色权限；轮换时可注册旧公钥继续验签。 */
@Component
public class RsaTokenProvider implements TokenProvider {
    private final RSAPrivateKey privateKey;
    private final Map<String, RSAPublicKey> keys = new java.util.concurrent.ConcurrentHashMap<>();
    private final String kid;
    private final String issuer;

    public RsaTokenProvider(
            @Value("${rbac.jwt.private-key-file:}") String privateFile,
            @Value("${rbac.jwt.public-key-file:}") String publicFile,
            @Value("${rbac.jwt.kid}") String kid,
            @Value("${rbac.jwt.issuer}") String issuer,
            @Value("${rbac.jwt.allow-ephemeral:false}") boolean ephemeral,
            Environment environment,
            @Value("${rbac.jwt.previous-public-keys:}") String previousKeys)
            throws Exception {
        this.kid = kid;
        this.issuer = issuer;
        DomainException.require(
                !kid.isBlank() && !issuer.isBlank(), 503, "JWT_CONFIGURATION_INVALID");
        if (!privateFile.isBlank() && !publicFile.isBlank()) {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            privateKey =
                    (RSAPrivateKey)
                            factory.generatePrivate(new PKCS8EncodedKeySpec(pem(privateFile)));
            keys.put(
                    kid,
                    (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(pem(publicFile))));
        } else {
            DomainException.require(
                    ephemeral
                            && Arrays.stream(environment.getActiveProfiles())
                                    .anyMatch(p -> p.equals("dev") || p.equals("test")),
                    503,
                    "JWT_KEY_REQUIRED");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            privateKey = (RSAPrivateKey) pair.getPrivate();
            keys.put(kid, (RSAPublicKey) pair.getPublic());
        }
        if (!previousKeys.isBlank())
            for (String entry : previousKeys.split(";")) {
                String[] pair = entry.split("=", 2);
                DomainException.require(
                        pair.length == 2 && !pair[0].isBlank() && !pair[0].equals(kid),
                        503,
                        "JWT_PREVIOUS_KEY_INVALID");
                keys.put(
                        pair[0],
                        (RSAPublicKey)
                                KeyFactory.getInstance("RSA")
                                        .generatePublic(new X509EncodedKeySpec(pem(pair[1]))));
            }
        byte[] challenge = "rbac-key-pair-check".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(challenge);
        byte[] signed = signature.sign();
        signature.initVerify(keys.get(kid));
        signature.update(challenge);
        DomainException.require(signature.verify(signed), 503, "JWT_KEY_PAIR_MISMATCH");
    }

    private byte[] pem(String file) throws Exception {
        return Base64.getDecoder()
                .decode(
                        Files.readString(Path.of(file))
                                .replaceAll("-----[^-]+-----", "")
                                .replaceAll("\\s", ""));
    }

    public void retainVerificationKey(String keyId, RSAPublicKey key) {
        DomainException.require(!keyId.equals(kid), 409, "ACTIVE_KEY_IMMUTABLE");
        keys.put(keyId, key);
    }

    @Override
    public String issue(String subject, String system, long version) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header()
                .keyId(kid)
                .and()
                .subject(subject)
                .issuer(issuer)
                .audience()
                .add(system)
                .and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(7200)))
                .id(UUID.randomUUID().toString())
                .claim("tokenVersion", version)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    @Override
    public Principal verify(String token) {
        try {
            Jws<Claims> parsed =
                    Jwts.parser()
                            .keyLocator(
                                    new LocatorAdapter<Key>() {
                                        @Override
                                        public Key locate(ProtectedHeader header) {
                                            DomainException.require(
                                                    "RS256".equals(header.getAlgorithm()),
                                                    401,
                                                    "TOKEN_INVALID");
                                            RSAPublicKey key = keys.get(header.getKeyId());
                                            DomainException.require(
                                                    key != null, 401, "TOKEN_INVALID");
                                            return key;
                                        }
                                    })
                            .requireIssuer(issuer)
                            .build()
                            .parseSignedClaims(token);
            Claims c = parsed.getPayload();
            Set<String> aud = c.getAudience();
            DomainException.require(
                    aud != null
                            && aud.size() == 1
                            && c.getSubject() != null
                            && c.getId() != null
                            && !c.getId().isBlank()
                            && c.getIssuedAt() != null
                            && c.getExpiration() != null
                            && c.getIssuedAt().toInstant().isBefore(Instant.now().plusSeconds(30))
                            && c.getExpiration().after(c.getIssuedAt())
                            && c.get("tokenVersion") instanceof Number,
                    401,
                    "TOKEN_INVALID");
            return new Principal(
                    c.getSubject(),
                    aud.iterator().next(),
                    c.getId(),
                    ((Number) c.get("tokenVersion")).longValue(),
                    c.getExpiration().toInstant());
        } catch (DomainException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DomainException(401, "TOKEN_INVALID");
        }
    }

    @Override
    public Map<String, Object> jwks() {
        List<Map<String, Object>> list = new ArrayList<>();
        keys.forEach(
                (id, key) ->
                        list.add(
                                Map.of(
                                        "kty",
                                        "RSA",
                                        "use",
                                        "sig",
                                        "alg",
                                        "RS256",
                                        "kid",
                                        id,
                                        "n",
                                        unsigned(key.getModulus()),
                                        "e",
                                        unsigned(key.getPublicExponent()))));
        return Map.of("keys", list);
    }

    private String unsigned(java.math.BigInteger number) {
        byte[] bytes = number.toByteArray();
        if (bytes[0] == 0) bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
