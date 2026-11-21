package com.xmu.rbac.ddd.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.rbac.ddd.application.AuthorizationApplicationService;
import com.xmu.rbac.ddd.domain.DomainException;
import com.xmu.rbac.ddd.domain.port.TokenProvider;
import com.xmu.rbac.gateway.CourseProxyFilter;
import com.xmu.rbac.gateway.CourseUpstream;
import com.xmu.rbac.gateway.GatewayFailure;
import java.io.IOException;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "course.upstream")
public class CourseProxyConfiguration {
    @Bean
    FilterRegistrationBean<CourseProxyFilter> courseProxy(
            @Value("${course.upstream}") URI origin, ObjectMapper mapper,
            TokenProvider tokens, AuthorizationApplicationService authorization) throws IOException {
        CourseProxyFilter filter = new CourseProxyFilter(
                mapper, CourseUpstream.defaults(origin), (bearer, operation) -> {
                    try {
                        var principal = tokens.verify(bearer.substring(7));
                        DomainException.require(CourseProxyFilter.COURSE_SYSTEM.equals(principal.systemCode()),
                                403, "AUDIENCE_FORBIDDEN");
                        var actor = authorization.validate(principal);
                        DomainException.require(authorization.api(actor.id(), principal.systemCode(), operation),
                                403, "COURSE_ACCESS_DENIED");
                    } catch (DomainException failure) {
                        if ("API_OPERATION_NOT_FOUND".equals(failure.code())) {
                            throw new GatewayFailure(403, "IDENTITY_OPERATION_NOT_FOUND");
                        }
                        throw new GatewayFailure(failure.status(), failure.code());
                    }
                });
        var registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
