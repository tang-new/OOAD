package cn.edu.xmu.projectmgt.integration;

import cn.edu.xmu.projectmgt.infrastructure.audit.client.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/** Post-commit synchronous audit acknowledgement, NOT a distributed transaction. */
@Component @Profile("integration") @Order(10)
public class ConfirmedAuditFilter extends OncePerRequestFilter {
    private final UnifiedAuditClient client;
    public ConfirmedAuditFilter(UnifiedAuditClient client) {this.client=client;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var cached=new ContentCachingResponseWrapper(response);
        String raw=request.getHeader("X-Request-ID"),id;
        try {id=UUID.fromString(raw).toString();} catch(Exception ignored) {id=UUID.randomUUID().toString();}
        response.setHeader("X-Request-ID",id);
        chain.doFilter(request,cached);
        var snapshot=(AuditSnapshots.Snapshot)request.getAttribute(AuditSnapshots.ATTRIBUTE);
        cached.setHeader("X-Audit-State",snapshot==null?"not-required":"pending");
        if(snapshot!=null && cached.getStatus()>=200 && cached.getStatus()<300) {
            try {
                var mapper=new tools.jackson.databind.ObjectMapper();
                tools.jackson.databind.JsonNode body=null;
                if(cached.getContentAsByteArray().length>0 && cached.getContentType()!=null && cached.getContentType().contains("json"))body=mapper.readTree(cached.getContentAsByteArray());
                String value=snapshot.objectId();
                if(value==null && body!=null) {
                    String field=snapshot.objectType().equals("oral_record")?"oralRecordId":"id";
                    if(body.has(field))value=body.get(field).asString();
                }
                if(value==null || !value.matches("[0-9]+"))throw new IllegalStateException("Audit object unresolved");
                var after=new LinkedHashMap<String,Object>();
                // Both real centers accept the teacher contract's flat safe fields.
                // Do not assume one implementation's extra operationId/result JSON keys exist in the other.
                if(body!=null) {
                    Object minimized=AuditSnapshots.safe(mapper.convertValue(body,Object.class));
                    if(minimized instanceof Map<?,?> values) values.forEach((entryKey,entryValue) -> after.put(entryKey.toString(),entryValue));
                }
                // Scope IDs stay server-derived typed IDs, not raw response IDs.
                after.putAll(snapshot.context());
                var write=new IdentityAuditWriteRequest(id,snapshot.type(),snapshot.objectType(),"cpm-"+snapshot.objectType()+"-"+value,
                    request.getAttribute(OperationGate.OPERATION).toString(),snapshot.before(),after);
                IdentityAuditLogResponse confirmed=null;
                // Only transport timeout/I/O failures are retried; same immutable request and UUID.
                for(int attempt=0;attempt<2;attempt++) {
                    try {confirmed=client.writeLog(request.getHeader("Authorization"),write);break;}
                    catch(ResourceAccessException timeout) {if(attempt==1)throw timeout;}
                }
                if(confirmed==null || confirmed.id()==null)throw new IllegalStateException("Audit not acknowledged");
                cached.setHeader("X-Audit-State","confirmed");
            } catch(Exception unavailable) {
                // The domain DB may have committed before the center acknowledged. Never claim success.
                cached.resetBuffer();cached.setStatus(503);cached.setHeader("X-Audit-State","unconfirmed");cached.setContentType("application/json");
                cached.getWriter().write("{\"code\":\"AUDIT_UNCONFIRMED\",\"message\":\"业务可能已提交，审计未确认。请勿盲目重放业务请求。\"}");
            }
        }
        cached.copyBodyToResponse();
    }
}
