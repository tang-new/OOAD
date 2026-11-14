package com.xmu.rbac.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 所有入口的追踪标识均由服务端产生，不信任外部传入的追踪头。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class TraceFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = TraceFilter.class.getName();
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, traceId);
        response.setHeader("X-Trace-Id", traceId);
        chain.doFilter(request, response);
    }
}
