package com.xmu.rbac.gateway.config;

import com.xmu.rbac.gateway.RouteRegistry;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.web.client.RestTemplate;
import java.net.http.HttpClient;

/** RS256 白名单及严格必填声明校验；中心撤销检查不由本地 JWT 替代。 */
@Configuration
public class SecurityConfiguration {
    @Bean
    JwtDecoder jwtDecoder(GatewayProperties properties, HttpClient client) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofMillis(properties.timeoutMs()));
        RestTemplate template = new RestTemplate(factory);
        template.setInterceptors(List.of(new com.xmu.rbac.gateway.BoundedExchange(65536, properties.timeoutMs())));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri().toString())
                .jwsAlgorithm(SignatureAlgorithm.RS256).restOperations(template).build();
        decoder.setJwtValidator(new com.xmu.rbac.gateway.CourseJwtValidator(properties.issuer()));
        return decoder;
    }
    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        RequestMatcher login = request -> RouteRegistry.isCourseLogin(request.getMethod(), request.getRequestURI());
        DefaultBearerTokenResolver bearer = new DefaultBearerTokenResolver();
        http.csrf(csrf -> csrf.disable()).sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/v1/actuator/health", "/api/v1/actuator/info").permitAll()
                        .requestMatchers(login).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resource -> resource
                        .bearerTokenResolver(request -> login.matches(request) ? null : bearer.resolve(request))
                        .jwt(jwt -> { }).authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setHeader("WWW-Authenticate", "Bearer");
                    response.setContentType("application/json");
                    response.getWriter().write("{\"code\":\"INVALID_TOKEN\",\"message\":\"身份认证失败\"}");
                }));
        return http.build();
    }
}
