package com.xmu.rbac.gateway;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

/** 错误输出不带异常消息、请求体或令牌。 */
@RestControllerAdvice
public final class GatewayErrors {
    @ExceptionHandler(GatewayFailure.class)
    public ResponseEntity<Map<String, String>> boundary(GatewayFailure failure) {
        return ResponseEntity.status(failure.status()).body(Map.of("code", failure.code(), "message", "课程请求被拒绝或服务不可用"));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unavailable(Exception ignored) {
        return ResponseEntity.status(503).body(Map.of("code", "GATEWAY_UNAVAILABLE", "message", "网关暂不可用"));
    }
}
