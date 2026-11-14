package com.xmu.rbac.ddd.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 不可变审计事实。幂等绑定来源系统、主体及规范化载荷摘要。 */
public record AuditFact(
        Long id,
        String requestId,
        String systemCode,
        String origin,
        Long actorId,
        String actorSubject,
        String actorName,
        List<String> actorRoles,
        String type,
        String objectType,
        String objectId,
        String target,
        String beforeJson,
        String afterJson,
        String result,
        String reason,
        String traceId,
        String payloadHash,
        Instant at) {
    public AuditFact {
        UUID.fromString(requestId);
        actorRoles = List.copyOf(actorRoles);
        DomainException.require(
                target != null && !target.isBlank() && target.length() <= 500,
                400,
                "INVALID_AUDIT_TARGET");
        // 冻结表使用 TIMESTAMP(3)：响应必须与保存值同精度，
        // 才能保证以 POST 返回时间构造的闭区间 [at, at] 查到该记录。
        at = Objects.requireNonNull(at, "审计时间不能为空").truncatedTo(ChronoUnit.MILLIS);
    }

    public void requireReplay(AuditFact proposed) {
        DomainException.require(
                systemCode.equals(proposed.systemCode)
                        && Objects.equals(actorSubject, proposed.actorSubject)
                        && payloadHash.equals(proposed.payloadHash),
                400,
                "AUDIT_DUPLICATE_CONFLICT");
    }
}
