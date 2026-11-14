package com.xmu.rbac.gateway;

import com.xmu.rbac.gateway.config.GatewayProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;

/** 课程转发入口，先解析清单再逐次中心鉴权，绝不接受客户端 operationId。 */
@RestController
public final class GatewayController {
    private final RouteRegistry routes;
    private final AuthBoundary auth;
    private final ProxyClient proxy;
    private final GatewayProperties properties;
    public GatewayController(RouteRegistry routes, AuthBoundary auth, ProxyClient proxy, GatewayProperties properties) {
        this.routes = routes;
        this.auth = auth;
        this.proxy = proxy;
        this.properties = properties;
    }
    @RequestMapping("/**")
    public ResponseEntity<byte[]> forward(HttpServletRequest request) throws IOException {
        String traceId = (String) request.getAttribute(TraceFilter.ATTRIBUTE);
        String operationId = routes.resolve(request.getMethod(), request.getRequestURI());
        auth.authorize(operationId, request.getHeader("Authorization"));
        byte[] bytes = BoundedBody.read(request.getInputStream(), properties.maxRequestBytes(), 413);
        ProxyClient.Result result = proxy.forward(request, bytes, traceId);
        HttpHeaders headers = new HttpHeaders();
        result.headers().forEach(headers::put);
        headers.set("X-Trace-Id", traceId);
        return ResponseEntity.status(result.status()).headers(headers).body(result.body());
    }
}
