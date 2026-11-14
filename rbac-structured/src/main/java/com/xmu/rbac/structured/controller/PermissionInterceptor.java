package com.xmu.rbac.structured.controller;
import com.xmu.rbac.structured.dto.Actor;
import com.xmu.rbac.structured.service.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.*;
/** HTTP鉴权入口：可信路由模板解析，拒绝任何未登记入口。 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {
    private final TokenService tokens;
    private final AuthorizationService az;
    private final RouteRegistry registry;
    private final AuditService audit;
    public PermissionInterceptor(
        TokenService tokens, AuthorizationService az, RouteRegistry registry, AuditService audit) {
        this.tokens = tokens;
        this.az = az;
        this.registry = registry;
        this.audit = audit;
    }
    @Override
    public boolean preHandle(
        HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        RouteRegistry.Route route = registry.route(request.getMethod(), path);
        Actor actor = null;
        try {
            if (route == null)
                throw BizException.forbidden("API_NOT_REGISTERED");
            if ("PUBLIC".equals(route.policy()))
                return true;
            actor = tokens.verify(request.getHeader("Authorization"));
            request.setAttribute("actor", actor);
            if (path.startsWith("/api/v1/") && "PROTECTED".equals(route.policy()))
                az.center(actor, route.permission());
            return true;
        } catch (BizException e) {
            throw e;
        }
    }
}
