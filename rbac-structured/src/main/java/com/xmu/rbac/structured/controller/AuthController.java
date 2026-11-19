package com.xmu.rbac.structured.controller;
import com.xmu.rbac.structured.dto.*;
import com.xmu.rbac.structured.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
/** 教师身份会话、权限判定和审计入口使用裸响应，actor 取自验证令牌。 */
@RestController
@Validated
public class AuthController {
    private final AuthenticationService identity;
    private final AuthorizationService az;
    private final TokenService tokens;
    private final AuditService audit;
    public AuthController(AuthenticationService identity, AuthorizationService az,
        TokenService tokens, AuditService audit) {
        this.identity = identity;
        this.az = az;
        this.tokens = tokens;
        this.audit = audit;
    }
    @PostMapping("/auth/login")
    public Object login(@Valid @RequestBody Inputs.Login input) {
        return identity.login(input);
    }
    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@RequestAttribute Actor actor) {
        identity.logout(actor);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/auth/me")
    public Object me(@RequestAttribute Actor actor) {
        return identity.externalMe(actor);
    }
    @GetMapping("/auth/permissions/{permission}")
    public Object permission(
        @RequestAttribute Actor actor, @PathVariable @Size(min = 1, max = 128) String permission) {
        return Views.map(
            "allowed", az.permission(actor.userId(), actor.systemCode(), "UI", permission));
    }
    @GetMapping("/auth/api-permissions/{operationId}")
    public Object api(
        @RequestAttribute Actor actor, @PathVariable @Size(min = 1, max = 128) String operationId) {
        return Views.map("operationId", operationId, "allowed",
            az.api(actor.userId(), actor.systemCode(), operationId));
    }
    @PostMapping("/auth/audit-logs")
    public ResponseEntity<Object> write(@RequestAttribute Actor actor,
        @RequestHeader(name = "X-Audit-Service-Key", required = false) String key,
        @Valid @RequestBody Inputs.Audit input) {
        return ResponseEntity.status(201).body(audit.external(actor, input, key));
    }
    @GetMapping("/auth/audit-logs")
    public Object query(@RequestAttribute Actor actor,
        @RequestHeader(name = "X-Audit-Service-Key", required = false) String key,
        @RequestParam(name = "actor", required = false) String actorName,
        @RequestParam(required = false) Long actorId, @RequestParam(required = false) String target,
        @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
        @RequestParam(defaultValue = "1") @Min(1) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return audit.query(
            actor, true, key, null, actorName, actorId, target, from, to, page, pageSize);
    }
    @GetMapping("/.well-known/jwks.json")
    public Object jwks() {
        return tokens.jwks();
    }
}
