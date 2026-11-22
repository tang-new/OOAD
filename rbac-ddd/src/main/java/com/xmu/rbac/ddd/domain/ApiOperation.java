package com.xmu.rbac.ddd.domain;

import java.util.Objects;

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
    /** 比较同一系统中冻结的路由登记字段。 */
    public boolean matchesDefinition(ApiOperation expected) {
        return expected != null
                && operationId.equals(expected.operationId())
                && method.equals(expected.method())
                && pathTemplate.equals(expected.pathTemplate())
                && Objects.equals(permissionCode, expected.permissionCode())
                && Objects.equals(requiredRole, expected.requiredRole())
                && policy == expected.policy()
                && enabled == expected.enabled();
    }

    public enum Policy {
        PUBLIC,
        AUTHENTICATED,
        PROTECTED
    }
}
