package com.xmu.rbac.gateway.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 仅管理员可配置的可信上游；明文 HTTP 必须显式选择。 */
@Validated
@ConfigurationProperties("gateway")
public record GatewayProperties(@NotNull URI identityBaseUrl, @NotNull URI courseBaseUrl,
        @NotBlank String issuer, @NotNull URI jwkSetUri, boolean allowHttp,
        @Min(100) @Max(30000) int timeoutMs,
        @Min(1) @Max(67108864) int maxRequestBytes,
        @Min(1) @Max(67108864) int maxResponseBytes) {
    public GatewayProperties {
        validate(identityBaseUrl, allowHttp, true);
        validate(courseBaseUrl, allowHttp, true);
        validate(jwkSetUri, allowHttp, false);
    }

    private static void validate(URI uri, boolean allowHttp, boolean base) {
        if (uri == null || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !("https".equals(uri.getScheme()) || allowHttp && "http".equals(uri.getScheme()))
                || base && !(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))) {
            throw new IllegalArgumentException("上游必须是明确受信的 HTTP(S) 地址；基址不得含路径、用户、查询或片段");
        }
    }
}
