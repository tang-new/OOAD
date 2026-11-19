package com.xmu.rbac.ddd.infrastructure.persistence;

import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;
import com.xmu.rbac.ddd.infrastructure.persistence.entity.*;

import jakarta.persistence.*;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** 资源仓储的独立 JPA 适配器。 */
@Repository
@Transactional(readOnly = true)
public class JpaResourceRepositoryAdapter implements ResourceRepository {
    @PersistenceContext private EntityManager em;

    private <T> T required(Class<T> type, long id) {
        T e = em.find(type, id);
        DomainException.require(e != null, 404, "RESOURCE_NOT_FOUND");
        return e;
    }

    private long system(String code) {
        return em.createQuery("select id from SystemEntity where code=:c", Long.class)
                .setParameter("c", code)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new DomainException(404, "SYSTEM_NOT_FOUND"));
    }

    private String code(long id) {
        return required(SystemEntity.class, id).getCode();
    }

    private Long permission(String system, String code, String kind) {
        if (code == null) return null;
        return em.createQuery(
                        "select id from PermissionEntity where systemId=:s and code=:c and kind=:k",
                        Long.class)
                .setParameter("s", system(system))
                .setParameter("c", code)
                .setParameter("k", kind)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new DomainException(404, "PERMISSION_NOT_FOUND"));
    }

    private Ui ui(UiResourceEntity e) {
        UiState state = cn.edu.xmu.clonefactory.util.CloneFactory.copy(new UiState(null, null, null, null, null, null, null, null, null), e);
        return new Ui(
                e.getId(),
                code(e.getSystemId()),
                state.parentId(),
                state.type(),
                state.code(),
                state.name(),
                state.route(),
                state.component(),
                state.sortOrder(),
                state.status(),
                state.permissionId() == null
                        ? null
                        : required(PermissionEntity.class, state.permissionId()).getCode());
    }

    public List<Ui> ui(String system) {
        return em
                .createQuery(
                        "from UiResourceEntity where systemId=:s order by sortOrder,id",
                        UiResourceEntity.class)
                .setParameter("s", system(system))
                .getResultList()
                .stream()
                .map(this::ui)
                .toList();
    }

    public Ui ui(long id) {
        return ui(required(UiResourceEntity.class, id));
    }

    public Ui saveUi(Ui u) {
        UiResourceEntity e =
                u.id() == null ? new UiResourceEntity() : required(UiResourceEntity.class, u.id());
        UiState state = cn.edu.xmu.clonefactory.util.CloneFactory.copy(
                new UiState(null, null, null, null, null, null,
                        u.sortOrder() == null ? 0 : u.sortOrder(),
                        u.status() == null ? "ENABLED" : u.status(),
                        permission(u.systemCode(), u.permissionCode(), "UI")), u);
        cn.edu.xmu.clonefactory.util.CloneFactory.copy(e, state);
        e.setSystemId(system(u.systemCode()));
        if (u.id() == null) em.persist(e);
        em.flush();
        return ui(e);
    }

    public void deleteUi(long id) {
        em.remove(required(UiResourceEntity.class, id));
    }

    private ApiOperation operation(ApiOperationEntity e) {
        return new ApiOperation(
                e.getId(),
                code(e.getSystemId()),
                e.getOperationId(),
                e.getMethod(),
                e.getPathTemplate(),
                e.getPermissionId() == null
                        ? null
                        : required(PermissionEntity.class, e.getPermissionId()).getCode(),
                e.getRequiredRole(),
                ApiOperation.Policy.valueOf(e.getAccessPolicy()),
                e.getEnabled());
    }

    public ApiOperation operation(long id) {
        return operation(required(ApiOperationEntity.class, id));
    }

    public AuthorizationRepository.Page<ApiOperation> operations(
            String system, String keyword, int page, int size) {
        String where =
                " where systemId=:s and (:kw is null or operationId like :kw or pathTemplate like"
                        + " :kw)";
        TypedQuery<ApiOperationEntity> q =
                em.createQuery(
                        "from ApiOperationEntity" + where + " order by id",
                        ApiOperationEntity.class);
        TypedQuery<Long> count =
                em.createQuery("select count(*) from ApiOperationEntity" + where, Long.class);
        for (Query query : List.of(q, count)) {
            query.setParameter("s", system(system));
            query.setParameter("kw", keyword == null ? null : "%" + keyword + "%");
        }
        return new AuthorizationRepository.Page<>(
                page,
                size,
                count.getSingleResult(),
                q.setFirstResult((page - 1) * size).setMaxResults(size).getResultList().stream()
                        .map(this::operation)
                        .toList());
    }

    public ApiOperation saveOperation(ApiOperation o) {
        ApiOperationEntity e =
                o.id() == 0 ? new ApiOperationEntity() : required(ApiOperationEntity.class, o.id());
        e.setSystemId(system(o.systemCode()));
        e.setOperationId(o.operationId());
        e.setMethod(o.method());
        e.setPathTemplate(o.pathTemplate());
        e.setPermissionId(permission(o.systemCode(), o.permissionCode(), "API"));
        e.setRequiredRole(o.requiredRole());
        e.setAccessPolicy(o.policy().name());
        e.setEnabled(o.enabled());
        if (o.id() == 0) em.persist(e);
        em.flush();
        return operation(e);
    }

    public List<Mapping> mappings(String system) {
        return em
                .createQuery(
                        "select m from MappingEntity m,UiResourceEntity u where m.uiResourceId=u.id"
                                + " and u.systemId=:s order by m.id",
                        MappingEntity.class)
                .setParameter("s", system(system))
                .getResultList()
                .stream()
                .map(e -> new Mapping(e.getUiResourceId(), e.getApiOperationId()))
                .toList();
    }

    public void mappings(String system, List<Mapping> mappings) {
        List<Long> ids =
                em.createQuery("select id from UiResourceEntity where systemId=:s", Long.class)
                        .setParameter("s", system(system))
                        .getResultList();
        if (!ids.isEmpty())
            em.createQuery("delete from MappingEntity where uiResourceId in :ids")
                    .setParameter("ids", ids)
                    .executeUpdate();
        for (Mapping m : mappings) {
            MappingEntity e = new MappingEntity();
            e.setUiResourceId(m.uiResourceId());
            e.setApiOperationId(m.apiOperationId());
            em.persist(e);
        }
    }

    private Permission permission(PermissionEntity e) {
        return new Permission(
                e.getId(),
                code(e.getSystemId()),
                e.getCode(),
                e.getName(),
                Permission.Kind.valueOf(e.getKind()),
                e.getStatus().equals("ENABLED"));
    }

    public Permission permission(long id) {
        return permission(required(PermissionEntity.class, id));
    }

    public AuthorizationRepository.Page<Permission> permissions(
            String system, String kind, String keyword, int page, int size) {
        String where =
                " where (:s is null or systemId=:s) and (:kind is null or kind=:kind) and (:kw is"
                        + " null or code like :kw or name like :kw)";
        TypedQuery<PermissionEntity> q =
                em.createQuery(
                        "from PermissionEntity" + where + " order by id", PermissionEntity.class);
        TypedQuery<Long> count =
                em.createQuery("select count(*) from PermissionEntity" + where, Long.class);
        for (Query query : List.of(q, count)) {
            query.setParameter("s", system == null ? null : system(system));
            query.setParameter("kind", kind);
            query.setParameter("kw", keyword == null ? null : "%" + keyword + "%");
        }
        return new AuthorizationRepository.Page<>(
                page,
                size,
                count.getSingleResult(),
                q.setFirstResult((page - 1) * size).setMaxResults(size).getResultList().stream()
                        .map(this::permission)
                        .toList());
    }

    public Permission savePermission(Permission p) {
        PermissionEntity e =
                p.id() == 0 ? new PermissionEntity() : required(PermissionEntity.class, p.id());
        e.setSystemId(system(p.systemCode()));
        e.setCode(p.code());
        e.setName(p.name());
        e.setKind(p.kind().name());
        e.setStatus(p.enabled() ? "ENABLED" : "DISABLED");
        if (p.id() == 0) em.persist(e);
        em.flush();
        return permission(e);
    }
}
