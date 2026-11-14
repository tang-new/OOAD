package com.xmu.rbac.gateway;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** 独立测试 HTTP 边界，RSA/JWKS/身份/课程 stub 仅存在于测试。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayHttpTest {
    static final RSAKey KEY = key("key1");
    static RSAKey active = KEY;
    static final HttpServer identity = server();
    static final HttpServer course = server();
    static final AtomicInteger calls = new AtomicInteger();
    static final AtomicInteger checks = new AtomicInteger();
    static volatile int identityStatus = 200;
    static volatile boolean allowed = true;
    static volatile long delay;
    static volatile int courseStatus = 200;
    static volatile String lastPath;
    static volatile byte[] lastBody;
    static volatile Headers lastHeaders;
    static volatile String checked;
    static volatile String padding = "";
    static volatile String overrideIdentity;
    static volatile boolean redirect;
    static volatile long identityBodyDelay;
    static volatile long courseBodyDelay;
    static volatile String responseBody = "course-response";
    @LocalServerPort int port;

    static {
        identity.createContext("/.well-known/jwks.json", exchange -> reply(exchange, 200, new JWKSet(active == KEY ? List.of(KEY.toPublicJWK()) : List.of(KEY.toPublicJWK(), active.toPublicJWK())).toString()));
        identity.createContext("/auth/api-permissions/", exchange -> {
            checks.incrementAndGet();
            checked = exchange.getRequestURI().getPath().substring("/auth/api-permissions/".length());
            assertNotNull(exchange.getRequestHeaders().getFirst("Authorization"));
            assertNull(exchange.getRequestHeaders().getFirst("X-Audit-Service-Key"));
            try { Thread.sleep(delay); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            if (identityStatus >= 300 && identityStatus < 400) {
                exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + course.getAddress().getPort() + "/me");
            }
            reply(exchange, identityStatus, overrideIdentity != null ? overrideIdentity : "{\"operationId\":\"" + checked + "\",\"allowed\":" + allowed + ",\"padding\":\"" + padding + "\"}");
        });
        course.createContext("/", exchange -> {
            calls.incrementAndGet();
            lastPath = exchange.getRequestURI().toString();
            lastHeaders = exchange.getRequestHeaders();
            lastBody = exchange.getRequestBody().readAllBytes();
            if (redirect) { exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + course.getAddress().getPort() + "/me"); }
            reply(exchange, courseStatus, courseStatus == 403 ? "{\"code\":\"OBJECT_DENIED\"}" : responseBody);
        });
        identity.start(); course.start();
    }
    static HttpServer server() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            return server;
        } catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
    static RSAKey key(String id) {
        try { return new RSAKeyGenerator(2048).keyID(id).generate(); }
        catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
    static void reply(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        long bodyDelay = exchange.getLocalAddress().getPort() == course.getAddress().getPort() ? courseBodyDelay
                : exchange.getRequestURI().getPath().startsWith("/auth/") ? identityBodyDelay : 0;
        try { Thread.sleep(bodyDelay); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        try (java.io.OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("gateway.identity-base-url", () -> "http://127.0.0.1:" + identity.getAddress().getPort());
        registry.add("gateway.course-base-url", () -> "http://127.0.0.1:" + course.getAddress().getPort());
        registry.add("gateway.jwk-set-uri", () -> "http://127.0.0.1:" + identity.getAddress().getPort() + "/.well-known/jwks.json");
        registry.add("gateway.issuer", () -> "test-issuer");
        registry.add("gateway.allow-http", () -> "true");
        registry.add("gateway.timeout-ms", () -> 1500);
        registry.add("gateway.max-request-bytes", () -> 1024);
        registry.add("gateway.max-response-bytes", () -> 1024);
    }
    @BeforeEach void reset() {
        calls.set(0); checks.set(0); identityStatus = 200; allowed = true; delay = 0; courseStatus = 200; overrideIdentity = null; padding = ""; responseBody = "course-response"; redirect = false; identityBodyDelay = 0; courseBodyDelay = 0;
    }
    @AfterAll static void close() { identity.stop(0); course.stop(0); }
    String token(RSAKey key, String audience, String issuer, Instant expires) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("student-1").jwtID(UUID.randomUUID().toString())
                .issuer(issuer).audience(audience).issueTime(Date.from(Instant.now().minusSeconds(3)))
                .expirationTime(Date.from(expires)).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
    String valid() throws Exception { return token(KEY, "course-project-management", "test-issuer", Instant.now().plusSeconds(90)); }
    HttpResponse<String> send(String path, String method, String body, String token, Map<String,String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).method(method, HttpRequest.BodyPublishers.ofString(body));
        if (token != null) { builder.header("Authorization", "Bearer " + token); }
        headers.forEach(builder::header);
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    /** 非契约状态即使携带允许决定也不能授权，更不能跟随中心重定向。 */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {201, 202, 206, 300, 302, 307, 308})
    void nonContractIdentityStatusesNeverAuthorize(int status) throws Exception {
        identityStatus = status;
        allowed = true;
        HttpResponse<String> response = send("/me", "GET", "", valid(), Map.of());
        assertAll("中心非200响应必须失败关闭：" + status,
                () -> assertEquals(503, response.statusCode()),
                () -> assertEquals(0, calls.get(), "课程上游不应收到任何请求"),
                () -> assertEquals(1, checks.get(), "必须真实请求一次中心"));
    }

    @Test void invalidTokensNeverReachEitherBoundary() throws Exception {
        assertEquals(401, send("/me", "GET", "", null, Map.of()).statusCode());
        for (String token : List.of(token(KEY,"wrong","test-issuer",Instant.now().plusSeconds(90)),
                token(KEY,"course-project-management","wrong",Instant.now().plusSeconds(90)),
                token(KEY,"course-project-management","test-issuer",Instant.now().minusSeconds(90)),
                token(key("unknown"),"course-project-management","test-issuer",Instant.now().plusSeconds(90)))) {
            assertEquals(401, send("/me", "GET", "", token, Map.of()).statusCode());
        }
        assertEquals(0, calls.get()); assertEquals(0, checks.get());
    }
    @Test void requiredClaimsAndAlgorithmAreEnforced() throws Exception {
        JWTClaimsSet base = SignedJWT.parse(valid()).getJWTClaimsSet();
        for (String missing : List.of("sub","iat","exp","jti")) {
            JWTClaimsSet claims = new JWTClaimsSet.Builder(base).claim(missing, null).build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(KEY));
            assertEquals(401, send("/me", "GET", "", jwt.serialize(), Map.of()).statusCode(), missing);
        }
        SignedJWT noKid = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), base);
        noKid.sign(new RSASSASigner(KEY));
        assertEquals(401, send("/me", "GET", "", noKid.serialize(), Map.of()).statusCode());
        SignedJWT wrongAlgorithm = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(KEY.getKeyID()).build(), base);
        wrongAlgorithm.sign(new com.nimbusds.jose.crypto.MACSigner(new byte[32]));
        assertEquals(401, send("/me", "GET", "", wrongAlgorithm.serialize(), Map.of()).statusCode());
        assertEquals(0, checks.get()); assertEquals(0, calls.get());
    }
    @Test void malformedIdentityIsNotAnAuthorization() throws Exception {
        for (String response : List.of("null", "{}", "{\"operationId\":\"other\",\"allowed\":true}", "{\"operationId\":\"getCurrentUser\"}")) {
            overrideIdentity = response;
            assertEquals(503, send("/me", "GET", "", valid(), Map.of()).statusCode());
        }
        assertEquals(0, calls.get());
    }
    @Test void binaryUploadAndRangeUseTheFixedBackend() throws Exception {
        byte[] bytes = new byte[]{0,1,2,(byte)255,13,10,42};
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/classes/7/roster/import"))
                .header("Authorization", "Bearer "+valid()).header("Content-Type", "multipart/form-data; boundary=boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        assertEquals(200, HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertArrayEquals(bytes,lastBody); assertEquals("importRoster",checked);
        assertEquals(200, send("/files/5", "GET", "", valid(), Map.of("Range","bytes=0-4","Accept","application/octet-stream")).statusCode());
        assertEquals("bytes=0-4",lastHeaders.getFirst("Range"));
        assertEquals("application/octet-stream",lastHeaders.getFirst("Accept"));
        assertEquals(2,calls.get());
    }
    @Test void forwardingAndHeaderIsolation() throws Exception {
        HttpResponse<String> response = send("/terms/1/courses?sort=name&url=http%3A%2F%2Fevil.example", "POST", "{\"name\":\"课程\"}", valid(),
                Map.of("Content-Type","application/json", "X-Rbac-System","rbac-center", "X-Forwarded-Host","evil.example",
                        "X-Audit-Service-Key","fake-secret", "Cookie","admin=true", "X-Trace-Id","forged"));
        assertEquals(200, response.statusCode()); assertEquals(1, calls.get()); assertEquals(1, checks.get());
        assertEquals("createCourse", checked);
        assertEquals("/terms/1/courses?sort=name&url=http%3A%2F%2Fevil.example", lastPath);
        assertEquals("{\"name\":\"课程\"}", new String(lastBody, StandardCharsets.UTF_8));
        for (String name : List.of("X-Rbac-System","X-Forwarded-Host","X-Audit-Service-Key","Cookie")) { assertNull(lastHeaders.getFirst(name)); }
        assertNotNull(lastHeaders.getFirst("Authorization"));
        assertEquals("application/json", lastHeaders.getFirst("Content-Type"));
        assertNotEquals("forged", lastHeaders.getFirst("X-Trace-Id"));
        UUID.fromString(response.headers().firstValue("X-Trace-Id").orElseThrow());
    }
    @Test void unknownAndIdentityDecisionsAreFailClosed() throws Exception {
        assertEquals(404, send("/not-registered", "GET", "", valid(), Map.of("X-Rbac-OperationId","getCurrentUser")).statusCode());
        assertEquals(0, checks.get());
        allowed = false;
        assertEquals(403, send("/me", "GET", "", valid(), Map.of()).statusCode());
        allowed = true; identityStatus = 404;
        assertEquals(403, send("/me", "GET", "", valid(), Map.of()).statusCode());
        identityStatus = 401;
        assertEquals(401, send("/me", "GET", "", valid(), Map.of()).statusCode());
        identityStatus = 403;
        assertEquals(403, send("/me", "GET", "", valid(), Map.of()).statusCode());
        identityStatus = 500;
        assertEquals(503, send("/me", "GET", "", valid(), Map.of()).statusCode());
        identityStatus = 200; delay = 3000;
        assertEquals(503, send("/me", "GET", "", valid(), Map.of()).statusCode());
        assertEquals(0, calls.get());
    }
    @Test void revocationOnNextRequestAndObjectDenial() throws Exception {
        String token = valid();
        courseStatus = 403;
        HttpResponse<String> response = send("/me", "GET", "", token, Map.of());
        assertEquals(403, response.statusCode()); assertEquals("{\"code\":\"OBJECT_DENIED\"}", response.body());
        allowed = false;
        assertEquals(403, send("/me", "GET", "", token, Map.of()).statusCode());
        assertEquals(1, calls.get()); assertEquals(2, checks.get());
    }
    @Test void rotatedJwkIsLoadedOverHttp() throws Exception {
        RSAKey rotated = key("rotated"); active = rotated;
        assertEquals(200, send("/me", "GET", "", token(rotated,"course-project-management","test-issuer",Instant.now().plusSeconds(90)), Map.of()).statusCode());
        active = KEY;
        assertEquals(1, calls.get());
    }
    @Test void pathAttacksAndSizeLimits() throws Exception {
        for (String path : List.of("/courses/../me", "/courses/%2e%2e/me", "/courses%2f1", "/courses%5c1", "/courses%252f1", "/courses;admin=1", "/courses//1", "/http://evil.example")) {
            assertTrue(send(path,"GET","",valid(),Map.of()).statusCode() >= 400, path);
        }
        assertEquals(0, calls.get());
        assertEquals(413, send("/terms/1/courses", "POST", "x".repeat(1025), valid(), Map.of()).statusCode());
        assertEquals(0, calls.get());
    }
    @Test void identityBodyIsBoundedAndCannotAuthorizeWhenOversized() throws Exception {
        padding = "x".repeat(2048);
        assertEquals(503, send("/me", "GET", "", valid(), Map.of()).statusCode());
        assertEquals(0, calls.get());
    }
    @Test void responseBoundsAndRedirects() throws Exception {
        responseBody = "x".repeat(2048);
        assertEquals(503, send("/me", "GET", "", valid(), Map.of()).statusCode());
        assertEquals(1, calls.get());
        responseBody = "redirect"; courseStatus = 302; redirect = true;
        assertEquals(302, send("/me", "GET", "", valid(), Map.of()).statusCode());
        assertEquals(2, calls.get());
    }
    @Test void responseBodyDeadlinesFailClosed() throws Exception {
        identityBodyDelay = 3000;
        assertEquals(503, send("/me", "GET", "", valid(), Map.of()).statusCode());
        assertEquals(0, calls.get());
        identityBodyDelay = 0; courseBodyDelay = 3000;
        assertEquals(503, send("/me", "GET", "", valid(), Map.of()).statusCode());
        assertEquals(1, calls.get());
    }
    @Test void healthIsNotACourseOperation() throws Exception {
        assertEquals(200, send("/api/v1/actuator/health", "GET", "", null, Map.of()).statusCode());
        assertEquals(0, calls.get()); assertEquals(0, checks.get());
    }
}
