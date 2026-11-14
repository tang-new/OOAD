package com.xmu.rbac.ddd.domain.port;

import com.xmu.rbac.ddd.domain.AuditFact;

import java.time.Instant;

/** 审计存储不依赖业务外键，不修改已经确认的事实。 */
public interface AuditRepository {
    AuditFact find(String requestId);

    AuditFact append(AuditFact fact);

    AuthorizationRepository.Page<AuditFact> query(
            String systemCode,
            boolean external,
            String actor,
            Long actorId,
            String target,
            Instant from,
            Instant to,
            int page,
            int pageSize);
}
