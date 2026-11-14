package com.xmu.rbac.ddd.interfaces.rest;

import com.xmu.rbac.ddd.application.AuditApplicationService;
import com.xmu.rbac.ddd.domain.DomainException;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 错误只返回稳定码和中文提示，不暴露异常消息或堆栈。 */
@RestControllerAdvice
public class ApiExceptionAdvice {
    private final AuditApplicationService audits;
    private final RestSupport rest;
    private final com.xmu.rbac.ddd.application.AuthorizationApplicationService authorization;

    public ApiExceptionAdvice(
            AuditApplicationService audits,
            RestSupport rest,
            com.xmu.rbac.ddd.application.AuthorizationApplicationService authorization) {
        this.audits = audits;
        this.rest = rest;
        this.authorization = authorization;
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, Object>> domain(
            DomainException error, jakarta.servlet.http.HttpServletRequest request) {
        if (error.status() == 403 || error.status() == 401) {
            try {
                com.xmu.rbac.ddd.domain.port.AuthorizationRepository.Identity actor = null;
                String system = "rbac-center";
                try {
                    com.xmu.rbac.ddd.domain.port.TokenProvider.Principal principal =
                            rest.principal(request.getHeader("Authorization"));
                    actor = authorization.validate(principal);
                    system = principal.systemCode();
                } catch (RuntimeException invalid) {
                    /* 无效身份不作为可信 actor。 */
                }
                audits.denied(actor, system, "访问拒绝", error.code());
            } catch (RuntimeException unavailable) {
                return response(503, "AUDIT_UNAVAILABLE", "审计服务不可用");
            }
        }
        return response(
                error.status(),
                error.code(),
                error.status() == 401 ? "身份认证失败" : error.status() == 403 ? "无权执行操作" : "操作未满足业务规则");
    }

    @ExceptionHandler({
        org.springframework.web.bind.MethodArgumentNotValidException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        jakarta.validation.ConstraintViolationException.class,
        IllegalArgumentException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class
    })
    public ResponseEntity<Map<String, Object>> invalid(Exception error) {
        return response(400, "INVALID_REQUEST", "请求格式错误");
    }

    @ExceptionHandler({
        org.springframework.dao.DataIntegrityViolationException.class,
        jakarta.persistence.OptimisticLockException.class,
        org.springframework.orm.ObjectOptimisticLockingFailureException.class
    })
    public ResponseEntity<Map<String, Object>> conflict(Exception error) {
        return response(409, "DATA_CONFLICT", "数据冲突");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unavailable(Exception error) {
        return response(503, "SERVICE_UNAVAILABLE", "服务暂不可用");
    }

    private ResponseEntity<Map<String, Object>> response(int status, String code, String message) {
        com.xmu.rbac.contract.ApiErrors.Error error = com.xmu.rbac.contract.ApiErrors.of(status, code);
        return ResponseEntity.status(error.status()).body(Map.of("code", error.code(), "message", error.message()));
    }
}
