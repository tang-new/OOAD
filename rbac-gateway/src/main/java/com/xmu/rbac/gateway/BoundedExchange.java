package com.xmu.rbac.gateway;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** 声明式鉴权与 JWKS 客户端的有界响应读取，防止信任边界上无限缓冲。 */
public final class BoundedExchange implements ClientHttpRequestInterceptor {
    private final int limit;
    private final int timeoutMs;
    public BoundedExchange(int limit, int timeoutMs) { this.limit = limit; this.timeoutMs = timeoutMs; }
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        try (ClientHttpResponse response = execution.execute(request, body)) {
            byte[] bytes = BoundedBody.readTimed(response.getBody(), limit, timeoutMs);
            return new BufferedResponse(response.getStatusCode(), response.getHeaders(), bytes);
        }
    }
    private record BufferedResponse(HttpStatusCode status, HttpHeaders headers, byte[] bytes) implements ClientHttpResponse {
        @Override public HttpStatusCode getStatusCode() { return status; }
        @Override public String getStatusText() { return ""; }
        @Override public HttpHeaders getHeaders() { return headers; }
        @Override public InputStream getBody() { return new ByteArrayInputStream(bytes); }
        @Override public void close() { }
    }
}
