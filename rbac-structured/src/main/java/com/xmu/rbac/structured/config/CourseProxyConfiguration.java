package com.xmu.rbac.structured.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.rbac.gateway.CourseProxyFilter;
import com.xmu.rbac.gateway.CourseUpstream;
import com.xmu.rbac.gateway.GatewayFailure;
import com.xmu.rbac.structured.service.AuthorizationService;
import com.xmu.rbac.structured.service.BizException;
import com.xmu.rbac.structured.service.TokenService;
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
            TokenService tokens, AuthorizationService authorization) throws IOException {
        CourseProxyFilter filter = new CourseProxyFilter(
                mapper, CourseUpstream.defaults(origin), (bearer, operation) -> {
                    try {
                        var actor = tokens.verify(bearer);
                        if (!CourseProxyFilter.COURSE_SYSTEM.equals(actor.systemCode())) {
                            throw new GatewayFailure(403, "AUDIENCE_FORBIDDEN");
                        }
                        if (!authorization.api(actor.userId(), actor.systemCode(), operation)) {
                            throw new GatewayFailure(403, "COURSE_ACCESS_DENIED");
                        }
                    } catch (BizException failure) {
                        if ("API_OPERATION_NOT_FOUND".equals(failure.code)) {
                            throw new GatewayFailure(403, "IDENTITY_OPERATION_NOT_FOUND");
                        }
                        throw new GatewayFailure(failure.status, failure.code);
                    }
                });
        var registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
