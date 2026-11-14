package com.xmu.rbac.contract;

/**
 * 对外错误词汇表：仅定义HTTP协议，不包含鉴权、事务或领域决策。
 * 两套独立实现可保留自己的内部拒绝原因，REST边界统一投影为此公共契约。
 */
public final class ApiErrors {
    private ApiErrors() {}

    public record Error(int status, String code, String message) {}

    public static Error of(int status, String reason) {
        String code = switch (reason) {
            case "TOKEN_INVALID", "TOKEN_REQUIRED", "SYSTEM_DISABLED" -> "UNAUTHENTICATED";
            case "CREDENTIAL_INVALID" -> "INVALID_CREDENTIALS";
            case "INVALID_REQUEST", "INVALID_PAGE", "INVALID_PAGE_OR_TIME", "INVALID_QUERY",
                 "INVALID_AUDIT_TYPE", "INVALID_AUDIT_OBJECT", "AUDIT_OBJECT_MISMATCH" -> "VALIDATION_ERROR";
            case "AUDIENCE_FORBIDDEN" -> "AUDIENCE_MISMATCH";
            case "DEPARTMENT_OUT_OF_SCOPE" -> "OUT_OF_MANAGE_SCOPE";
            case "GRANT_EXCEEDS_ACTOR" -> "PRIVILEGE_ESCALATION";
            case "AUDIT_SERVICE_FORBIDDEN" -> "AUDIT_SERVICE_REQUIRED";
            case "FORBIDDEN" -> "PERMISSION_DENIED";
            case "API_UNREGISTERED" -> "API_NOT_REGISTERED";
            default -> reason;
        };
        int http = ("CROSS_SYSTEM_ROLE".equals(code) || "CROSS_SYSTEM_MAPPING".equals(code)) ? 400 : status;
        String message = switch (http) {
            case 400 -> "请求参数无效";
            case 401 -> "身份认证失败";
            case 403 -> "无权执行操作";
            case 404 -> "资源不存在";
            case 409 -> "操作冲突";
            default -> "服务暂时不可用";
        };
        return new Error(http, code, message);
    }
}
