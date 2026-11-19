package cn.edu.xmu.projectmgt.integration;

import cn.edu.xmu.projectmgt.infrastructure.audit.client.IdentityAuditLogResponse;
import cn.edu.xmu.projectmgt.infrastructure.audit.client.IdentityAuditWriteRequest;
import cn.edu.xmu.projectmgt.infrastructure.audit.client.UnifiedAuditClient;
import cn.edu.xmu.projectmgt.service.port.audit.AuditLogWriteCommand;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 业务提交后同步确认中心审计，同一请求使用固定幂等键。 */
@Component
@Profile("integration")
@Order(10)
public class ConfirmedAuditFilter extends OncePerRequestFilter {
    private static final String DOMAIN_EVENT = "course.audit.domain-event";
    private final UnifiedAuditClient client;
    private final ObjectMapper mapper;

    public ConfirmedAuditFilter(UnifiedAuditClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    /** 由教师审计适配器在事务提交后调用，集成请求统一由本过滤器写入。 */
    public static boolean capture(AuditLogWriteCommand command) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getRequest().getAttribute(OperationGate.OPERATION) != null) {
            attributes.getRequest().setAttribute(DOMAIN_EVENT, command);
            return true;
        }
        return false;
    }

    /** 在成功响应发送前收集一次中心确认，保留业务已提交但审计未确认的状态。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingResponseWrapper cached = new ContentCachingResponseWrapper(response);
        String id = requestId(request.getHeader("X-Request-ID"));
        cached.setHeader("X-Request-ID", id);
        chain.doFilter(request, cached);
        AuditSnapshots.Snapshot snapshot = (AuditSnapshots.Snapshot) request.getAttribute(AuditSnapshots.ATTRIBUTE);
        AuditLogWriteCommand event = (AuditLogWriteCommand) request.getAttribute(DOMAIN_EVENT);
        boolean required = snapshot != null || event != null;
        cached.setHeader("X-Audit-State", required ? "pending" : "not-required");
        if (required && cached.getStatus() >= 200 && cached.getStatus() < 300) {
            try {
                JsonNode body = cached.getContentAsByteArray().length > 0 && cached.getContentType() != null
                        && cached.getContentType().contains("json")
                        ? mapper.readTree(cached.getContentAsByteArray()) : null;
                String operation = (String) request.getAttribute(OperationGate.OPERATION);
                confirm(request.getHeader("Authorization"), auditWrite(id, operation, event, snapshot, body));
                cached.setHeader("X-Audit-State", "confirmed");
            } catch (Exception unavailable) {
                cached.resetBuffer();
                cached.setStatus(503);
                cached.setHeader("X-Audit-State", "unconfirmed");
                cached.setContentType("application/json");
                cached.getOutputStream().write("""
                        {"code":"AUDIT_UNCONFIRMED","message":"业务可能已提交，审计未确认。请勿盲目重放业务请求。"}
                        """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        cached.copyBodyToResponse();
    }

    /** 原生事件补充准确状态，统一快照策略剔除文本、联系方式和其他未授权字段。 */
    private IdentityAuditWriteRequest auditWrite(String id, String operation, AuditLogWriteCommand event,
            AuditSnapshots.Snapshot snapshot, JsonNode body) {
        String type = event == null ? snapshot.type() : event.type();
        String objectType = event == null ? snapshot.objectType() : event.objectType();
        String objectId = event == null ? objectId(snapshot, body) : event.objectId();
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        if (body != null) {
            mergeSafe(after, mapper.convertValue(body, Object.class));
        }
        if (snapshot != null) {
            mergeSafe(before, snapshot.before());
        }
        if (event != null) {
            mergeSafe(before, event.before());
            mergeSafe(after, event.after());
        }
        if (snapshot != null) {
            after.putAll(snapshot.context());
        }
        return new IdentityAuditWriteRequest(id, type, objectType, objectId, operation,
                before.isEmpty() ? null : before, after);
    }

    /** 旧版覆盖的方法从路由或成功结果中提取对象标识。 */
    private static String objectId(AuditSnapshots.Snapshot snapshot, JsonNode body) {
        String value = snapshot.objectId();
        if (value == null && body != null) {
            String field = "oral_record".equals(snapshot.objectType()) ? "oralRecordId" : "id";
            if (body.has(field)) {
                value = body.get(field).asString();
            }
        }
        if (value == null || !value.matches("[0-9]+")) {
            throw new IllegalStateException("无法确定业务审计对象");
        }
        return "cpm-" + snapshot.objectType() + "-" + value;
    }

    /** 对待写入的快照统一执行字段白名单。 */
    private static void mergeSafe(Map<String, Object> output, Object value) {
        if (AuditSnapshots.safe(value) instanceof Map<?, ?> fields) {
            fields.forEach((key, field) -> output.put(key.toString(), field));
        }
    }

    /** 中心仅在传输故障时收到完全相同幂等键及内容的重试。 */
    private void confirm(String bearer, IdentityAuditWriteRequest write) {
        IdentityAuditLogResponse confirmed;
        try {
            confirmed = client.writeLog(bearer, write);
        } catch (ResourceAccessException timeout) {
            confirmed = client.writeLog(bearer, write);
        }
        if (confirmed == null || confirmed.id() == null) {
            throw new IllegalStateException("中心未确认审计");
        }
    }

    /** 保留调用方给出的规范 UUID，其余请求生成新的幂等键。 */
    private static String requestId(String supplied) {
        try {
            return UUID.fromString(supplied).toString();
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return UUID.randomUUID().toString();
        }
    }
}
