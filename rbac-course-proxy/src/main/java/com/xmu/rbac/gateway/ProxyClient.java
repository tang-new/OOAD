package com.xmu.rbac.gateway;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 只向固定课程 origin 转发；不跟随重定向，头部白名单阻断伪造的服务身份。 */
public final class ProxyClient {
    private static final Set<String> REQUEST_HEADERS = Set.of("authorization", "x-request-id", "content-type", "accept", "range", "if-range", "if-none-match", "if-modified-since");
    private static final Set<String> RESPONSE_HEADERS = Set.of("content-type", "content-range", "accept-ranges", "etag", "last-modified", "cache-control", "content-disposition", "x-request-id", "x-audit-state");
    private final CourseUpstream properties;
    private final HttpClient client;

    public ProxyClient(CourseUpstream properties, HttpClient client) {
        this.properties = properties;
        this.client = client;
    }

    public Result forward(HttpServletRequest request, String path, byte[] body, String traceId, boolean login) {
        String query = request.getQueryString();
        RouteRegistry.validatePath(path);
        URI target;
        try {
            target = URI.create(properties.origin().toString().replaceAll("/$", "")
                    + path + (query == null ? "" : "?" + query));
            if (!target.getHost().equals(properties.origin().getHost()) || target.getRawFragment() != null) {
                throw new GatewayFailure(400, "INVALID_COURSE_QUERY");
            }
        } catch (IllegalArgumentException exception) { throw new GatewayFailure(400, "INVALID_COURSE_QUERY"); }
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(Duration.ofMillis(properties.timeoutMs()))
                .method(request.getMethod(), HttpRequest.BodyPublishers.ofByteArray(body))
                .header("X-Trace-Id", traceId);
        Set<String> connectionTokens = connectionTokens(request.getHeader("Connection"));
        for (String header : REQUEST_HEADERS) {
            if (!connectionTokens.contains(header) && !(login && "authorization".equals(header))) {
                java.util.Enumeration<String> values = request.getHeaders(header);
                while (values.hasMoreElements()) { builder.header(header, values.nextElement()); }
            }
        }
        try {
            HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                byte[] bytes = BoundedBody.readTimed(input, properties.maxResponseBytes(), properties.timeoutMs());
                Map<String, List<String>> headers = new java.util.HashMap<>();
                Set<String> responseConnection = connectionTokens(response.headers().firstValue("Connection").orElse(null));
                response.headers().map().forEach((name, values) -> {
                    if (RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT)) && !responseConnection.contains(name.toLowerCase(Locale.ROOT))) {
                        headers.put(name, values);
                    }
                });
                return new Result(response.statusCode(), headers, bytes);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GatewayFailure(503, "COURSE_UNAVAILABLE");
        } catch (IOException exception) {
            throw new GatewayFailure(503, "COURSE_UNAVAILABLE");
        }
    }

    static Set<String> connectionTokens(String connection) {
        if (connection == null) { return Set.of(); }
        return Set.copyOf(java.util.Arrays.asList(connection.toLowerCase(Locale.ROOT).replace(" ", "").split(",")));
    }

    /** 课程端对象级拒绝保持原状态及原字节，不转换为中心资格。 */
    public record Result(int status, Map<String, List<String>> headers, byte[] body) { }
}
