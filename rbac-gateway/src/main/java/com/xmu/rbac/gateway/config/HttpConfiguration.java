package com.xmu.rbac.gateway.config;

import com.xmu.rbac.gateway.IdentityClient;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/** 所有出站连接固定可信配置、有限超时、无重定向。 */
@Configuration
public class HttpConfiguration {
    @Bean
    HttpClient httpClient(GatewayProperties properties) {
        return HttpClient.newBuilder().connectTimeout(Duration.ofMillis(properties.timeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    @Bean
    IdentityClient identityClient(GatewayProperties properties, HttpClient client) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofMillis(properties.timeoutMs()));
        RestClient rest = RestClient.builder().baseUrl(properties.identityBaseUrl().toString())
                .requestFactory(factory).requestInterceptor(new com.xmu.rbac.gateway.BoundedExchange(properties.maxResponseBytes(), properties.timeoutMs())).build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(rest)).build().createClient(IdentityClient.class);
    }
}
