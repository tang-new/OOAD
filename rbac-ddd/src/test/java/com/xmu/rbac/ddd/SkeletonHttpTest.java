package com.xmu.rbac.ddd;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.URI;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** 通过随机端口的真实 HTTP 验证无外部服务时的启动契约，不使用 MockMvc。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SkeletonHttpTest {

    @Autowired
    private TestRestTemplate http;

    @LocalServerPort
    private int port;

    private URI endpoint(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    @Test
    void healthIsUpAndExplicitlyNotBusinessReady() {
        ResponseEntity<JsonNode> response = http.getForEntity(endpoint("/api/v1/actuator/health"), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("status").asText()).isEqualTo("UP");
        JsonNode details = body.path("components").path("skeleton").path("details");
        assertMetadata(details);
        assertThat(body.path("components").has("db")).isFalse();
        assertThat(body.path("components").has("redis")).isFalse();
    }

    @Test
    void infoExposesOnlyNonSensitiveStageMetadata() {
        ResponseEntity<JsonNode> response = http.getForEntity(endpoint("/api/v1/actuator/info"), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertMetadata(response.getBody());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/users", "/users/1", "/roles", "/permissions", "/auth/login"})
    void unimplementedBusinessGetReturns404(String path) {
        assertThat(http.getForEntity(endpoint("/api/v1" + path), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unimplementedLoginPostReturns404() {
        ResponseEntity<String> response = http.exchange(endpoint("/api/v1/auth/login"), HttpMethod.POST,
                new HttpEntity<>(java.util.Map.of("username", "nobody", "password", "not-a-secret")),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"env", "beans", "configprops", "metrics", "mappings", "loggers", "heapdump", "shutdown"})
    void otherActuatorEndpointsAreNotExposed(String endpoint) {
        assertThat(http.getForEntity(endpoint("/api/v1/actuator/" + endpoint), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void healthIsNotExposedOutsideConfiguredContextPath() {
        assertThat(http.getForEntity(endpoint("/actuator/health"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static void assertMetadata(JsonNode metadata) {
        assertThat(metadata).isNotNull();
        assertThat(metadata.path("backend").asText()).isEqualTo("ddd");
        assertThat(metadata.path("stage").asText()).isEqualTo("M2-skeleton");
        assertThat(metadata.path("businessReady").isBoolean()).isTrue();
        assertThat(metadata.path("businessReady").booleanValue()).isFalse();
        assertThat(metadata.size()).isEqualTo(3);
    }
}
