package com.xmu.rbac.ddd.domain;

/** 独立 API 策略；无要求的 PROTECTED 操作默认拒绝。 */
public record ApiOperation(
        long id,
        String systemCode,
        String operationId,
        String method,
        String pathTemplate,
        String permissionCode,
        String requiredRole,
        Policy policy,
        boolean enabled) {
    public enum Policy {
        PUBLIC,
        AUTHENTICATED,
        PROTECTED
    }
}
