package com.xmu.rbac.ddd.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;
import com.xmu.rbac.ddd.infrastructure.persistence.entity.AuditEntity;

import jakarta.persistence.*;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/** 不可变审计持久化，唯一冲突由事务外的应用编排重新读取。 */
@Repository
@Transactional(readOnly = true)
public class JpaAuditRepositoryAdapter implements AuditRepository {
    @PersistenceContext private EntityManager em;
    private final ObjectMapper mapper;

    public JpaAuditRepositoryAdapter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new DomainException(400, "INVALID_AUDIT_JSON");
        }
    }

    private AuditFact restore(AuditEntity e) {
        try {
            List<String> roles =
                    mapper.readValue(
                            e.getActorRolesJson(),
                            mapper.getTypeFactory()
                                    .constructCollectionType(List.class, String.class));
            return new AuditFact(
                    e.getId(),
                    e.getRequestId(),
                    e.getSourceSystemCode(),
                    e.getOrigin(),
                    e.getActorId(),
                    e.getActorSubject(),
                    e.getActorName(),
                    roles,
                    e.getEventType(),
                    e.getObjectType(),
                    e.getObjectId(),
                    e.getTarget(),
                    e.getBeforeJson(),
                    e.getAfterJson(),
                    e.getResult(),
                    e.getReason(),
                    e.getTraceId(),
                    e.getPayloadHash(),
                    e.getOccurredAt());
        } catch (Exception ex) {
            throw new DomainException(503, "AUDIT_UNAVAILABLE");
        }
    }

    public AuditFact find(String requestId) {
        return em.createQuery("from AuditEntity where requestId=:r", AuditEntity.class)
                .setParameter("r", requestId)
                .getResultStream()
                .findFirst()
                .map(this::restore)
                .orElse(null);
    }

    public AuditFact append(AuditFact fact) {
        AuditEntity e = new AuditEntity();
        e.setRequestId(fact.requestId());
        e.setSourceSystemCode(fact.systemCode());
        e.setOrigin(fact.origin());
        e.setActorId(fact.actorId());
        e.setActorSubject(fact.actorSubject());
        e.setActorName(fact.actorName());
        e.setActorRolesJson(json(fact.actorRoles()));
        e.setEventType(fact.type());
        e.setObjectType(fact.objectType());
        e.setObjectId(fact.objectId());
        e.setTarget(fact.target());
        e.setBeforeJson(fact.beforeJson());
        e.setAfterJson(fact.afterJson());
        e.setResult(fact.result());
        e.setReason(fact.reason());
        e.setTraceId(fact.traceId());
        e.setPayloadHash(fact.payloadHash());
        e.setOccurredAt(fact.at());
        em.persist(e);
        em.flush();
        return restore(e);
    }

    public AuthorizationRepository.Page<AuditFact> query(
            String system,
            boolean external,
            String actor,
            Long actorId,
            String target,
            Instant from,
            Instant to,
            int page,
            int size) {
        String where =
                " where (:system is null or sourceSystemCode=:system) and (:actor is null or"
                        + " actorName like :actor) and (:actorId is null or actorId=:actorId) and"
                        + " (:target is null or target like :target) and (:from is null or"
                        + " occurredAt>=:from) and (:to is null or occurredAt<=:to)"
                        + (external
                                ? " and origin='COURSE' and eventType in"
                                      + " ('score_changed','group_changed','milestone_changed','roster_changed')"
                                : "");
        TypedQuery<AuditEntity> q =
                em.createQuery(
                        "from AuditEntity" + where + " order by occurredAt desc,id desc",
                        AuditEntity.class);
        TypedQuery<Long> count =
                em.createQuery("select count(*) from AuditEntity" + where, Long.class);
        for (Query query : List.of(q, count)) {
            query.setParameter("system", system);
            query.setParameter("actor", actor == null ? null : "%" + actor + "%");
            query.setParameter("actorId", actorId);
            query.setParameter("target", target == null ? null : "%" + target + "%");
            query.setParameter("from", from);
            query.setParameter("to", to);
        }
        return new AuthorizationRepository.Page<>(
                page,
                size,
                count.getSingleResult(),
                q.setFirstResult((page - 1) * size).setMaxResults(size).getResultList().stream()
                        .map(this::restore)
                        .toList());
    }
}
