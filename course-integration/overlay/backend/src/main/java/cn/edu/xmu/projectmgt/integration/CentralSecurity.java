package cn.edu.xmu.projectmgt.integration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods=false)
@Profile("integration")
public class CentralSecurity {
    /** Explicit, finite timeouts; no incoming service-key header is ever forwarded. */
    public static RestClient client(String url, String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Integration service key required");
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().baseUrl(url).requestFactory(factory)
            .requestInterceptor((request, body, execution) -> {
                request.getHeaders().set("X-Audit-Service-Key", key);
                return execution.execute(request, body);
            }).build();
    }
    @Bean
    CentralClient centralClient(
            @Value("${app.identity.base-url}")
            String url,
            @Value("${app.integration.service-key}")
            String key
    ) {
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client(url,key))).build().createClient(CentralClient.class);
    }
    @Bean
    JwtDecoder integrationJwtDecoder(
            @Value("${app.integration.jwk-set-uri}")
            String jwks,
            @Value("${app.integration.issuer}")
            String issuer
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).jwsAlgorithm(SignatureAlgorithm.RS256).build();
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains("course-project-management")
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong audience", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audience));
        return decoder;
    }
    @Bean
    SecurityFilterChain integrationSecurity(HttpSecurity http, JwtDecoder decoder) throws Exception {
        DefaultBearerTokenResolver bearer = new DefaultBearerTokenResolver();
        return http.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(OperationGate::isCourseLogin).permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o
                .bearerTokenResolver(request -> OperationGate.isCourseLogin(request) ? null : bearer.resolve(request))
                .jwt(j -> j.decoder(decoder)
                // JWT role/permission claims deliberately ignored. The MVC gate replaces authorities.
                .jwtAuthenticationConverter(jwt -> new JwtAuthenticationToken(jwt, List.of(), jwt.getSubject()))))
            .build();
    }
}
