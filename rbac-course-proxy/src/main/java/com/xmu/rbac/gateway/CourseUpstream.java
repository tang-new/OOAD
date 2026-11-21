package com.xmu.rbac.gateway;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/** 固定课程上游及转发限额，从部署配置读取。 */
public record CourseUpstream(URI origin, int timeoutMs, int maxRequestBytes, int maxResponseBytes) {
    public CourseUpstream {
        if (origin == null || origin.getHost() == null
                || !("http".equals(origin.getScheme()) || "https".equals(origin.getScheme()))
                || origin.getRawUserInfo() != null || origin.getRawQuery() != null
                || origin.getRawFragment() != null
                || !(origin.getRawPath().isEmpty() || "/".equals(origin.getRawPath()))) {
            throw new IllegalArgumentException("课程上游必须是固定的 HTTP(S) origin");
        }
        if (timeoutMs < 100 || timeoutMs > 30_000
                || maxRequestBytes < 1 || maxRequestBytes > 67_108_864
                || maxResponseBytes < 1 || maxResponseBytes > 67_108_864) {
            throw new IllegalArgumentException("课程转发超时或字节限额无效");
        }
    }

    public static CourseUpstream defaults(URI origin) {
        return new CourseUpstream(origin, 2_000, 10_485_760, 10_485_760);
    }

    public HttpClient httpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
}
