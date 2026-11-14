package com.xmu.rbac.structured.service;
import static com.xmu.rbac.structured.service.Views.*;

import com.xmu.rbac.structured.dto.Actor;
import com.xmu.rbac.structured.entity.*;
import com.xmu.rbac.structured.mapper.Store;
import io.jsonwebtoken.*;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
/** RS256单aud令牌；撤销和用户版本始终查询持久化事实。 */
@Service
public class TokenService {
    private final PrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final Map<String, RSAPublicKey> verificationKeys;
    private final String kid, issuer;
    private final Store db;
    private final AuthorizationService authz;
    public TokenService(Store db, AuthorizationService authz, Environment env,
        @Value("${rbac.jwt.private-key-file:}") String privateFile,
        @Value("${rbac.jwt.public-key-file:}") String publicFile,
        @Value("${rbac.jwt.kid}") String kid, @Value("${rbac.jwt.issuer}") String issuer,
        @Value("${rbac.jwt.allow-ephemeral:false}") boolean ephemeral,
        @Value("${rbac.jwt.previous-public-keys:}") String previousPublicKeys) {
        this.db = db;
        this.authz = authz;
        this.kid = kid;
        this.issuer = issuer;
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            if (privateFile.isBlank() != publicFile.isBlank())
                throw new IllegalStateException("私钥和公钥必须成对配置");
            if (!privateFile.isBlank() && !publicFile.isBlank()) {
                privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(pem(privateFile)));
                publicKey =
                    (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(pem(publicFile)));
            } else {
                if (!ephemeral
                    || Arrays.stream(env.getActiveProfiles())
                        .noneMatch(p -> p.equals("test") || p.equals("dev")))
                    throw new IllegalStateException("必须配置共享RSA密钥");
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair pair = generator.generateKeyPair();
                privateKey = pair.getPrivate();
                publicKey = (RSAPublicKey) pair.getPublic();
            }
            if (kid.isBlank() || issuer.isBlank())
                throw new IllegalStateException("kid和issuer不能为空");
            if (publicKey.getModulus().bitLength() < 2048)
                throw new IllegalStateException("RSA密钥长度不能小于2048位");
            Signature probe = Signature.getInstance("SHA256withRSA");
            byte[] challenge =
                "rbac-key-pair-validation".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            probe.initSign(privateKey);
            probe.update(challenge);
            byte[] signedChallenge = probe.sign();
            probe.initVerify(publicKey);
            probe.update(challenge);
            if (!probe.verify(signedChallenge))
                throw new IllegalStateException("私钥与公钥不匹配");
            LinkedHashMap<String, RSAPublicKey> keys = new LinkedHashMap<>();
            keys.put(kid, publicKey);
            Map<String, String> previous = parsePrevious(previousPublicKeys);
            for (Map.Entry<String, String> entry : previous.entrySet()) {
                if (entry.getKey().isBlank() || keys.containsKey(entry.getKey()))
                    throw new IllegalStateException("轮换kid重复或为空");
                RSAPublicKey previousKey = (RSAPublicKey) factory.generatePublic(
                    new X509EncodedKeySpec(pem(entry.getValue())));
                if (previousKey.getModulus().bitLength() < 2048)
                    throw new IllegalStateException("历史RSA公钥长度不能小于2048位");
                keys.put(entry.getKey(), previousKey);
            }
            verificationKeys = Collections.unmodifiableMap(keys);
        } catch (Exception e) {
            throw new IllegalStateException("JWT密钥配置无效", e);
        }
    }
    private byte[] pem(String path) throws Exception {
        String text =
            Files.readString(Path.of(path)).replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(text);
    }
    /** 签发不含角色和权限声明的单一aud令牌。 */
    public Map<String, Object> issue(User u, String code) {
        Instant now = Instant.now();
        String token = Jwts.builder()
                           .header()
                           .keyId(kid)
                           .and()
                           .subject(u.subject)
                           .issuer(issuer)
                           .audience()
                           .add(code)
                           .and()
                           .issuedAt(Date.from(now))
                           .expiration(Date.from(now.plusSeconds(7200)))
                           .id(UUID.randomUUID().toString())
                           .claim("tokenVersion", u.tokenVersion)
                           .signWith(privateKey, Jwts.SIG.RS256)
                           .compact();
        return map("accessToken", token, "tokenType", "Bearer", "expiresIn", 7200);
    }
    /** 校验令牌签名、声明以及持久化实时身份状态。 */
    public Actor verify(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer "))
            throw BizException.unauthorized();
        try {
            Jws<Claims> signed = Jwts.parser()
                                     .keyLocator(header -> {
                                         RSAPublicKey key = verificationKeys.get(header.get("kid"));
                                         if (key == null || !"RS256".equals(header.get("alg")))
                                             throw new UnsupportedJwtException("未知签名密钥");
                                         return key;
                                     })
                                     .requireIssuer(issuer)
                                     .build()
                                     .parseSignedClaims(authorization.substring(7));
            Claims c = signed.getPayload();
            Instant now = Instant.now();
            if (!"RS256".equals(signed.getHeader().getAlgorithm())
                || !verificationKeys.containsKey(signed.getHeader().getKeyId())
                || c.getAudience() == null || c.getAudience().size() != 1 || c.getSubject() == null
                || c.getSubject().isBlank() || c.getId() == null || c.getId().isBlank()
                || c.getIssuedAt() == null || c.getIssuedAt().toInstant().isAfter(now)
                || c.getExpiration() == null || !c.getExpiration().toInstant().isAfter(now)
                || !c.getExpiration().after(c.getIssuedAt()) || c.containsKey("roles")
                || c.containsKey("permissions"))
                throw BizException.unauthorized();
            String code = c.getAudience().iterator().next();
            SoftwareSystem s = db.one(SoftwareSystem.class, "e.code=?1", code);
            if (s == null)
                throw BizException.unauthorized();
            User u = db.one(User.class, "e.subject=?1", c.getSubject());
            if (u == null)
                throw BizException.unauthorized();
            authz.active(u, s);
            Object version = c.get("tokenVersion");
            if (!(version instanceof Number) || ((Number) version).longValue() != u.tokenVersion
                || db.get(TokenRevocation.class, c.getId()) != null)
                throw BizException.unauthorized();
            return new Actor(u.id, u.subject, code, c.getId(), c.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException e) {
            throw BizException.unauthorized();
        }
    }
    /** 当前签名公钥与仍在保留期内的历史验证公钥同时发布。 */
    public Map<String, Object> jwks() {
        List<Map<String, Object>> keys = new ArrayList<>();
        for (Map.Entry<String, RSAPublicKey> entry : verificationKeys.entrySet())
            keys.add(map("kty", "RSA", "kid", entry.getKey(), "use", "sig", "alg", "RS256", "n",
                integer(entry.getValue().getModulus()), "e",
                integer(entry.getValue().getPublicExponent())));
        return map("keys", keys);
    }
    /** 历史公钥配置支持JSON对象或kid=公钥文件路径的逗号分隔清单。 */
    private Map<String, String> parsePrevious(String config) throws Exception {
        if (config == null || config.isBlank())
            return Map.of();
        if (config.stripLeading().startsWith("{"))
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(config,
                new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, String>>() {
                });
        Map<String, String> result = new LinkedHashMap<>();
        for (String entry : config.split(",")) {
            String[] pair = entry.split("=", 2);
            if (pair.length != 2 || pair[1].isBlank()
                || result.put(pair[0].trim(), pair[1].trim()) != null)
                throw new IllegalStateException("历史公钥配置无效");
        }
        return result;
    }

    private String integer(java.math.BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes[0] == 0)
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
