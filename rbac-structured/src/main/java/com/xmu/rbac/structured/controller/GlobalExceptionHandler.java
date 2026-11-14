package com.xmu.rbac.structured.controller;
import com.xmu.rbac.structured.service.*;
import jakarta.persistence.*;
import jakarta.validation.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
/** 统一错误出口，不输出异常消息、堆栈或请求值。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private final AuditService audit;
    public GlobalExceptionHandler(AuditService audit) {
        this.audit = audit;
    }
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Object> business(
        BizException e, jakarta.servlet.http.HttpServletRequest request) {
        if (e.status == 401 || e.status == 403 || e.status == 409)
            audit.denied((com.xmu.rbac.structured.dto.Actor) request.getAttribute("actor"), e.code);
        return error(e.status, e.code);
    }
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        ConstraintViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<Object> input(Exception e) {
        return error(400, "VALIDATION_ERROR");
    }
    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockException.class,
        org.springframework.orm.ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<Object> conflict(Exception e) {
        return error(409, "DATA_CONFLICT");
    }
    @ExceptionHandler({DataAccessException.class, PersistenceException.class,
        org.springframework.transaction.TransactionException.class})
    public ResponseEntity<Object> database(Exception e) {
        return error(503, "SERVICE_UNAVAILABLE");
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unknown(Exception e) {
        return error(503, "SERVICE_UNAVAILABLE");
    }
    private ResponseEntity<Object> error(int status, String code) {
        com.xmu.rbac.contract.ApiErrors.Error error = com.xmu.rbac.contract.ApiErrors.of(status, code);
        return ResponseEntity.status(error.status()).body(Views.map("code", error.code(), "message", error.message()));
    }
}
