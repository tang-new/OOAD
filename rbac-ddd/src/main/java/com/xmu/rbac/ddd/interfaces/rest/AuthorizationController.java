package com.xmu.rbac.ddd.interfaces.rest;

import com.xmu.rbac.ddd.application.*;
import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/** 受控管理鉴权解释与内部审计查询。 */
@RestController
@RequestMapping("/api/v1")
public class AuthorizationController {
    private final RestSupport rest;
    private final AuthorizationApplicationService authorization;
    private final DirectoryApplicationService directory;
    private final AuditApplicationService audits;

    public AuthorizationController(
            RestSupport rest,
            AuthorizationApplicationService authorization,
            DirectoryApplicationService directory,
            AuditApplicationService audits) {
        this.rest = rest;
        this.authorization = authorization;
        this.directory = directory;
        this.audits = audits;
    }

    public record Check(
            @NotBlank @Size(max = 64) String systemCode,
            @Positive Long userId,
            @NotNull Permission.Kind kind,
            @Size(min = 1, max = 128) String permission,
            @Size(min = 1, max = 128) String operationId) {}

    private Object check(String bearer, Check i, boolean explain) {
        TokenProvider.Principal principal = rest.principal(bearer);
        AuthorizationRepository.Identity actor =
                authorization.require(principal, "rbac:authz:invoke", false);
        DomainException.require(
                i.kind() == Permission.Kind.UI
                        ? i.permission() != null && i.operationId() == null
                        : i.operationId() != null && i.permission() == null,
                400,
                "INVALID_AUTHORIZATION_REQUEST");
        long user = i.userId() == null ? actor.id() : i.userId();
        if (user != actor.id()) directory.user(principal, user);
        var decision = authorization.simulate(user, i.systemCode(), i.kind(),
                i.kind() == Permission.Kind.UI ? i.permission() : i.operationId());
        String unavailable = decision.unavailableReason();
        boolean allowed = decision.allowed();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("allowed", allowed);
        result.put("reason", unavailable != null ? unavailable : allowed ? "ALLOW" : "DENY");
        result.put("traceId", Optional.ofNullable(MDC.get("traceId"))
                .orElseGet(() -> UUID.randomUUID().toString()));
        if (explain) {
            result.put("trace", unavailable != null ? List.of("ACTOR_AUTHENTICATED", unavailable)
                    : List.of("TOKEN_VALID", "USER_SYSTEM_ACTIVE", allowed ? "REQUIREMENT_SATISFIED" : "REQUIREMENT_NOT_SATISFIED"));
            result.put("effective", RestSupport.effective(decision.effective()));
        }
        return RestSupport.ok(result);
    }

    @PostMapping("/authorization/check")
    public Object check(
            @RequestHeader(value = "Authorization", required = false) String b,
            @Valid @RequestBody Check i) {
        return check(b, i, false);
    }

    @PostMapping("/authorization/explain")
    public Object explain(
            @RequestHeader(value = "Authorization", required = false) String b,
            @Valid @RequestBody Check i) {
        return check(b, i, true);
    }

    @GetMapping("/audit-logs")
    public Object audit(
            @RequestHeader(value = "Authorization", required = false) String b,
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String target,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        authorization.require(rest.principal(b), "rbac:audit:view", false);
        AuthorizationRepository.Page<AuditFact> result =
                audits.query(systemCode, false, actor, actorId, target, from, to, page, pageSize);
        return RestSupport.ok(
                Map.of(
                        "page",
                        page,
                        "pageSize",
                        pageSize,
                        "total",
                        result.total(),
                        "items",
                        result.items().stream().map(rest::audit).toList()));
    }
}
