package com.xmu.rbac.ddd.interfaces.rest;

import com.xmu.rbac.ddd.application.AuthorizationApplicationService;
import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;

import jakarta.servlet.http.*;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;

import java.util.*;

/** 以服务端匹配路由解析 operationId；未登记的受保护 API 默认拒绝。 */
@Component
public class RegisteredApiInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    private final OperationContract contract;
    private final RestSupport rest;
    private final AuthorizationApplicationService authorization;

    public RegisteredApiInterceptor(
            OperationContract contract,
            RestSupport rest,
            AuthorizationApplicationService authorization) {
        this.contract = contract;
        this.rest = rest;
        this.authorization = authorization;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this)
                .addPathPatterns(
                        "/api/v1/**",
                        "/auth/**",
                        "/.well-known/**",
                        "/integration/**",
                        "/identity/**");
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        String pattern =
                (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern == null) throw new DomainException(403, "API_UNREGISTERED");
        if (pattern.startsWith("/api/v1/actuator/")) return true;
        ApiOperation operation =
                contract.operations().stream()
                        .filter(
                                o ->
                                        o.method().equals(request.getMethod())
                                                && o.pathTemplate().equals(pattern))
                        .findFirst()
                        .orElseThrow(() -> new DomainException(403, "API_UNREGISTERED"));
        if (operation.policy() == ApiOperation.Policy.PUBLIC) return true;
        authorization.requireRegisteredOperation(operation.operationId());
        TokenProvider.Principal principal = rest.principal(request.getHeader("Authorization"));
        if (pattern.startsWith("/api/v1/")
                && !Set.of("/api/v1/me", "/api/v1/menus/me", "/api/v1/auth/password")
                        .contains(pattern))
            authorization.require(principal, operation.permissionCode(), false);
        return true;
    }
}
