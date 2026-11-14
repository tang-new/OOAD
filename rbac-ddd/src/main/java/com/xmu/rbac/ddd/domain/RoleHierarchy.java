package com.xmu.rbac.ddd.domain;

import java.util.*;

/** 同系统 DAG 值图；保留所有有效路径用于解释菱形来源。调用方须串行化图写。 */
public final class RoleHierarchy {
    public record Edge(long seniorRoleId, long juniorRoleId) {}

    private final Map<Long, Role> roles = new LinkedHashMap<>();
    private final Set<Edge> edges;

    public RoleHierarchy(Collection<Role> roles, Collection<Edge> edges) {
        for (Role role : roles) this.roles.put(role.id(), role);
        this.edges = new LinkedHashSet<>(edges);
        for (Edge edge : this.edges) {
            Role senior = required(edge.seniorRoleId());
            Role junior = required(edge.juniorRoleId());
            DomainException.require(
                    senior.systemCode().equals(junior.systemCode()), 409, "CROSS_SYSTEM_ROLE");
            DomainException.require(
                    edge.seniorRoleId() != edge.juniorRoleId()
                            && !reachable(
                                    edge.juniorRoleId(), edge.seniorRoleId(), new HashSet<>()),
                    409,
                    "ROLE_CYCLE");
        }
    }

    public void link(long senior, long junior) {
        Role a = required(senior);
        Role b = required(junior);
        DomainException.require(a.systemCode().equals(b.systemCode()), 409, "CROSS_SYSTEM_ROLE");
        DomainException.require(
                senior != junior && !reachable(junior, senior, new HashSet<>()), 409, "ROLE_CYCLE");
        DomainException.require(edges.add(new Edge(senior, junior)), 409, "ROLE_EDGE_EXISTS");
    }

    public void unlink(long senior, long junior) {
        DomainException.require(edges.remove(new Edge(senior, junior)), 404, "ROLE_EDGE_NOT_FOUND");
    }

    private boolean reachable(long from, long target, Set<Long> visited) {
        if (from == target) return true;
        if (!visited.add(from)) return false;
        for (Edge edge : edges)
            if (edge.seniorRoleId() == from && reachable(edge.juniorRoleId(), target, visited))
                return true;
        return false;
    }

    private Role required(long id) {
        Role role = roles.get(id);
        DomainException.require(role != null, 404, "ROLE_NOT_FOUND");
        return role;
    }

    public List<List<Long>> paths(Set<Long> directIds) {
        List<List<Long>> result = new ArrayList<>();
        for (Long id : new TreeSet<>(directIds)) visit(id, new ArrayList<>(), result);
        return List.copyOf(result);
    }

    private void visit(long id, List<Long> prefix, List<List<Long>> result) {
        Role role = roles.get(id);
        if (role == null || !role.enabled() || prefix.contains(id)) return;
        if (!prefix.isEmpty())
            DomainException.require(
                    required(prefix.getFirst()).systemCode().equals(role.systemCode()),
                    409,
                    "CROSS_SYSTEM_ROLE");
        List<Long> path = new ArrayList<>(prefix);
        path.add(id);
        result.add(List.copyOf(path));
        edges.stream()
                .filter(e -> e.seniorRoleId() == id)
                .sorted(Comparator.comparingLong(Edge::juniorRoleId))
                .forEach(e -> visit(e.juniorRoleId(), path, result));
    }

    public Collection<Role> roles() {
        return List.copyOf(roles.values());
    }

    public Set<Edge> edges() {
        return Set.copyOf(edges);
    }

    /** 检查特殊能力时不切断停用节点，防止预埋继承边等待超级管理员恢复。 */
    public void requireGrantable(Set<Long> direct, boolean actorSuperAdministrator) {
        if (actorSuperAdministrator) return;
        for (Long id : direct)
            for (Role role : roles.values())
                DomainException.require(
                        !role.superAdministratorCapability()
                                || !reachable(id, role.id(), new HashSet<>()),
                        403,
                        "GRANT_EXCEEDS_ACTOR");
    }
}
