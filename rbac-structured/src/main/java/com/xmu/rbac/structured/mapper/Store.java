package com.xmu.rbac.structured.mapper;
import com.xmu.rbac.structured.entity.*;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Repository;
/** JPA 数据访问边界；分页和过滤在数据库完成，锁必须由调用事务持有。 */
@Repository
public class Store {
    @PersistenceContext private EntityManager em;
    private final Map<Class<?>, org.springframework.data.jpa.repository.JpaRepository<?, ?>>
        repositories;
    private final UserMapper users;
    private final GrantVersionMapper versions;
    public Store(SoftwareSystemMapper softwareSystem, DepartmentMapper department, RoleMapper role,
        PermissionMapper permission, RolePermissionMapper rolePermission, UserRoleMapper userRole,
        DepartmentAllowedRoleMapper departmentAllowedRole, RoleHierarchyMapper roleHierarchy,
        UiResourceMapper uiResource, ApiOperationMapper apiOperation,
        UiApiMappingMapper uiApiMapping, TokenRevocationMapper tokenRevocation,
        AuditLogMapper auditLog, UserMapper user, GrantVersionMapper grantVersion) {
        this.repositories = Map.ofEntries(Map.entry(SoftwareSystem.class, softwareSystem),
            Map.entry(Department.class, department), Map.entry(Role.class, role),
            Map.entry(Permission.class, permission),
            Map.entry(RolePermission.class, rolePermission), Map.entry(UserRole.class, userRole),
            Map.entry(DepartmentAllowedRole.class, departmentAllowedRole),
            Map.entry(RoleHierarchy.class, roleHierarchy), Map.entry(UiResource.class, uiResource),
            Map.entry(ApiOperation.class, apiOperation),
            Map.entry(UiApiMapping.class, uiApiMapping),
            Map.entry(TokenRevocation.class, tokenRevocation), Map.entry(AuditLog.class, auditLog),
            Map.entry(User.class, user), Map.entry(GrantVersion.class, grantVersion));
        this.users = user;
        this.versions = grantVersion;
    }
    @SuppressWarnings("unchecked")
    private <T> org.springframework.data.jpa.repository.JpaRepository<T, Object> repository(
        Class<T> type) {
        org.springframework.data.jpa.repository.JpaRepository<?, ?> repository =
            repositories.get(type);
        if (repository == null)
            throw new IllegalArgumentException("实体没有持久化接口");
        return (org.springframework.data.jpa.repository.JpaRepository<T, Object>) repository;
    }
    /** 执行get操作，保持服务到持久化层的单向依赖。 */
    public <T> T get(Class<T> type, Object id) {
        return repository(type).findById(id).orElse(null);
    }
    /** 持有排他锁。 */
    public <T> T lock(Class<T> type, Object id) {
        if (type == User.class)
            return type.cast(users.lockById((Long) id));
        if (type == GrantVersion.class)
            return type.cast(versions.lockById((Long) id));
        return em.find(type, id, LockModeType.PESSIMISTIC_WRITE);
    }
    /** 执行save操作，保持服务到持久化层的单向依赖。 */
    @SuppressWarnings("unchecked")
    public <T> T save(T value) {
        return repository((Class<T>) value.getClass()).save(value);
    }
    /** 执行remove操作，保持服务到持久化层的单向依赖。 */
    @SuppressWarnings("unchecked")
    public void remove(Object value) {
        repository((Class<Object>) value.getClass()).delete(value);
    }
    /** 执行flush操作，保持服务到持久化层的单向依赖。 */
    public void flush() {
        em.flush();
    }
    /** 执行all操作，保持服务到持久化层的单向依赖。 */
    public <T> List<T> all(Class<T> type) {
        return repository(type).findAll();
    }
    /** 执行where操作，保持服务到持久化层的单向依赖。 */
    public <T> List<T> where(Class<T> type, String clause, Object... args) {
        TypedQuery<T> q =
            em.createQuery("from " + type.getSimpleName() + " e where " + clause, type);
        for (int i = 0; i < args.length; i++) q.setParameter(i + 1, args[i]);
        return q.getResultList();
    }
    /** 执行one操作，保持服务到持久化层的单向依赖。 */
    public <T> T one(Class<T> type, String clause, Object... args) {
        return where(type, clause, args).stream().findFirst().orElse(null);
    }
    /** 执行count操作，保持服务到持久化层的单向依赖。 */
    public <T> long count(Class<T> type) {
        return repository(type).count();
    }
    /** 在数据库执行稳定排序的分页查询。 */
    public <T> Map<String, Object> page(Class<T> type, String clause, List<Object> args, int page,
        int size, java.util.function.Function<T, Object> view, String order) {
        String from = " from " + type.getSimpleName() + " e where " + clause;
        TypedQuery<Long> count = em.createQuery("select count(e)" + from, Long.class);
        TypedQuery<T> query = em.createQuery("select e" + from + " order by " + order, type);
        for (int i = 0; i < args.size(); i++) {
            count.setParameter(i + 1, args.get(i));
            query.setParameter(i + 1, args.get(i));
        }
        long offset = (long) (page - 1) * size;
        long total = count.getSingleResult();
        List<Object> items = offset > Integer.MAX_VALUE ? List.of()
                                                        : query.setFirstResult((int) offset)
                                                              .setMaxResults(size)
                                                              .getResultList()
                                                              .stream()
                                                              .map(view)
                                                              .toList();
        return Map.of("page", page, "pageSize", size, "total", total, "items", items);
    }
}
