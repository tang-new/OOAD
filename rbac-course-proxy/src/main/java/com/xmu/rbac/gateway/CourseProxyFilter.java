package com.xmu.rbac.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;

/** 在 MVC 解析上传请求前鉴权并转发，中心管理路由继续交给原有链路。 */
public final class CourseProxyFilter implements Filter {
    public static final String COURSE_SYSTEM = "course-project-management";

    @FunctionalInterface
    public interface Authorize {
        void require(String bearer, String operationId);
    }

    private final ObjectMapper json;
    private final CourseUpstream upstream;
    private final Authorize authorize;
    private final RouteRegistry routes;
    private final ProxyClient proxy;

    public CourseProxyFilter(ObjectMapper json, CourseUpstream upstream, Authorize authorize)
            throws IOException {
        this.json = json;
        this.upstream = upstream;
        this.authorize = authorize;
        this.routes = new RouteRegistry(json);
        this.proxy = new ProxyClient(upstream, upstream.httpClient());
    }

    @Override
    public void doFilter(ServletRequest input, ServletResponse output, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) input;
        HttpServletResponse response = (HttpServletResponse) output;
        String rawPath = request.getRequestURI().substring(request.getContextPath().length());
        if (!rawPath.startsWith("/api/") || rawPath.equals("/api/v1") || rawPath.startsWith("/api/v1/")) {
            chain.doFilter(input, output);
            return;
        }

        String traceId = UUID.randomUUID().toString();
        response.setHeader("X-Trace-Id", traceId);
        response.setHeader("X-Content-Type-Options", "nosniff");
        ProxyClient.Result result;
        try {
            String coursePath = rawPath.substring("/api".length());
            String operation = routes.resolve(request.getMethod(), coursePath);
            if ("courseLogin".equals(operation)) {
                throw new GatewayFailure(404, "COURSE_OPERATION_NOT_FOUND");
            }
            Enumeration<String> headers = request.getHeaders("Authorization");
            String bearer = headers.hasMoreElements() ? headers.nextElement() : "";
            if (!bearer.startsWith("Bearer ") || bearer.length() <= 7 || headers.hasMoreElements()) {
                throw new GatewayFailure(401, "TOKEN_REQUIRED");
            }
            authorize.require(bearer, operation);
            byte[] body = BoundedBody.read(request.getInputStream(), upstream.maxRequestBytes(), 413);
            result = proxy.forward(request, coursePath, body, traceId, false);
        } catch (GatewayFailure failure) {
            error(response, failure.status(), failure.code());
            return;
        } catch (IOException failure) {
            error(response, 400, "COURSE_REQUEST_UNREADABLE");
            return;
        } catch (RuntimeException failure) {
            error(response, 503, "COURSE_PROXY_UNAVAILABLE");
            return;
        }

        response.setStatus(result.status());
        result.headers().forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
        if (!response.containsHeader("Cache-Control")) response.setHeader("Cache-Control", "no-store");
        response.getOutputStream().write(result.body());
    }

    private void error(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        json.writeValue(response.getOutputStream(), Map.of("code", code, "message", "课程请求被拒绝或服务不可用"));
    }
}
