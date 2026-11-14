package com.xmu.rbac.ddd;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

/** 所有真实数据库 HTTP 测试复用相同上下文，只连接专用测试数据库与 Redis。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.url=jdbc:mysql://127.0.0.1:13306/rbac1_ddd_test?serverTimezone=UTC&useSSL=false&useLocalSessionState=true&connectTimeout=5000&socketTimeout=120000",
            "spring.data.redis.timeout=30s",
            "spring.data.redis.connect-timeout=30s",
            "rbac.jwt.allow-ephemeral=true",
            "RBAC_BOOTSTRAP_PASSWORD=Ddd-Test-Only-Password-2026!",
            "rbac.audit-service-key=ddd-integration-service-only"
        })
@ActiveProfiles("test")
abstract class DddHttpTestSupport {
    @Autowired protected TestRestTemplate http;
    protected final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void useHttpClientThatReturns401Responses() {
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(180)).build());
        factory.setReadTimeout(Duration.ofSeconds(180));
        http.getRestTemplate().setRequestFactory(factory);
    }

    protected String login(String username, String system) {
        ResponseEntity<JsonNode> result =
                http.postForEntity(
                        "/auth/login",
                        Map.of(
                                "username",
                                username,
                                "password",
                                "Ddd-Test-Only-Password-2026!",
                                "systems",
                                List.of(system)),
                        JsonNode.class);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        return result.getBody().path("accessToken").asText();
    }

    protected ResponseEntity<JsonNode> call(
            HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    protected JsonNode successful(HttpMethod method, String path, String token, Object body) {
        ResponseEntity<JsonNode> result = call(method, path, token, body);
        assertThat(result.getStatusCode())
                .as("%s %s: %s", method, path, result.getBody())
                .isEqualTo(HttpStatus.OK);
        return result.getBody().path("data");
    }

    protected Map<String, Object> values(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
}
