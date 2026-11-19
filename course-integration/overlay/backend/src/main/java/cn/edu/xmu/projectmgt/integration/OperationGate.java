package cn.edu.xmu.projectmgt.integration;

import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.client.RestClientException;

/** Routes come from the pinned server contract, never from client-supplied operation headers. */
@Configuration(proxyBeanMethods=false)
@Profile("integration")
public class OperationGate implements WebMvcConfigurer, HandlerInterceptor {
    public static final String OPERATION = "course.integration.operation";
    private final CentralClient central;
    private final AuditSnapshots snapshots;
    private final Map<String,String> routes = new HashMap<>();
    public OperationGate(CentralClient central, AuditSnapshots snapshots) throws IOException {
        this.central=central; this.snapshots=snapshots;
        try (InputStream input = getClass().getResourceAsStream("/integration-routes.tsv")) {
            if(input==null) throw new IOException("Pinned operation map missing");
            for(String line:new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                if(line.isBlank()) continue;
                String[] values=line.strip().split("\t"); routes.put(values[0], values[1]);
            }
        }
    }
    /** 精确识别教师契约中的匿名登录入口。 */
    static boolean isCourseLogin(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && "/login".equals(request.getRequestURI());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }

    @Override
    public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) throws Exception {
        if(request.getDispatcherType() == jakarta.servlet.DispatcherType.ERROR
                || request.getRequestURI().equals("/actuator/health")) return true;
        Object pattern=request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String operation=pattern==null?null:routes.get(request.getMethod()+" "+pattern.toString().replaceAll("\\{[^}]+}","{}"));
        if ("courseLogin".equals(operation) && isCourseLogin(request)) {
            return true;
        }
        if(operation==null) { response.sendError(403); return false; }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(!(authentication instanceof JwtAuthenticationToken jwt)) { response.sendError(401);return false; }
        // JWT already verified locally; these calls recheck revocation, user and system at the center.
        String bearer="Bearer "+jwt.getToken().getTokenValue();
        try {
            CentralClient.Decision decision = central.operation(bearer,operation);
            if(decision==null || !decision.allowed()) {response.sendError(403);return false;}
            CentralClient.Authorities snapshot = central.authorities(bearer);
            if(snapshot==null || snapshot.authorities()==null) {response.sendError(503);return false;}
            List<SimpleGrantedAuthority> authorities = snapshot.authorities().stream().filter(Objects::nonNull).map(SimpleGrantedAuthority::new).toList();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt.getToken(),authorities,jwt.getName()));
            request.setAttribute(OPERATION,operation);
            snapshots.prepare(request,operation);
            return true;
        } catch(RestClientException failure) {
            // Never expose token, key, upstream body or exception message.
            response.sendError(503);return false;
        }
    }
}
