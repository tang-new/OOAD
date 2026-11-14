package com.xmu.rbac.gateway;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/** 每次真实查询中心，持久撤销和状态以中心决定为准，失败关闭。 */
@Component
public final class AuthBoundary {
    private final IdentityClient identity;
    public AuthBoundary(IdentityClient identity) { this.identity = identity; }

    public void authorize(String operationId, String bearer) {
        ResponseEntity<IdentityClient.Decision> response;
        try {
            response = identity.check(operationId, bearer);
        } catch (HttpClientErrorException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403) { throw new GatewayFailure(status, "IDENTITY_DENIED"); }
            if (status == 404) { throw new GatewayFailure(403, "IDENTITY_OPERATION_NOT_FOUND"); }
            throw new GatewayFailure(503, "IDENTITY_UNAVAILABLE");
        } catch (RuntimeException exception) {
            throw new GatewayFailure(503, "IDENTITY_UNAVAILABLE");
        }
        // 不跟随重定向不等于鉴权成功；只有契约明确规定的200响应才可解释决定。
        if (response == null || response.getStatusCode().value() != 200) {
            throw new GatewayFailure(503, "IDENTITY_INVALID_RESPONSE");
        }
        IdentityClient.Decision decision = response.getBody();
        if (decision == null || !operationId.equals(decision.operationId()) || decision.allowed() == null) {
            throw new GatewayFailure(503, "IDENTITY_INVALID_RESPONSE");
        }
        if (!decision.allowed()) { throw new GatewayFailure(403, "COURSE_ACCESS_DENIED"); }
    }
}
