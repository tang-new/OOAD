package com.xmu.rbac.ddd.application;

import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** 角色用例只做锁、加载、领域协作、保存和同事务审计。 */
@Service
public class RoleApplicationService {
    private final GrantIntegrityService integrity;
    private final AuthorizationRepository repository;
    private final AuthorizationApplicationService authorization;
    private final AuditApplicationService audits;
    private final AuthorizationPolicy policy = new AuthorizationPolicy();

    public RoleApplicationService(
            AuthorizationRepository repository,
            AuthorizationApplicationService authorization,
            AuditApplicationService audits,
            GrantIntegrityService integrity) {
        this.integrity = integrity;
        this.repository = repository;
        this.authorization = authorization;
        this.audits = audits;
    }

    public AuthorizationRepository.Page<Role> list(
            TokenProvider.Principal principal,
            String system,
            String keyword,
            String status,
            int page,
            int size) {
        authorization.require(principal, "rbac:role:view", false);
        DomainException.require(page > 0 && size > 0 && size <= 100, 400, "INVALID_PAGE");
        return repository.roles(system, keyword, status, page, size);
    }

    public Role get(TokenProvider.Principal principal, long id) {
        authorization.require(principal, "rbac:role:view", false);
        return repository.role(id);
    }

    public RoleHierarchy hierarchy(TokenProvider.Principal principal, String system) {
        authorization.require(principal, "rbac:role:view", false);
        return repository.hierarchy(system);
    }

    public AuthorizationPolicy.Effective effective(TokenProvider.Principal principal, long id) {
        Role role = get(principal, id);
        return effectiveRole(role);
    }

    private AuthorizationPolicy.Effective effectiveRole(Role role) {
        return policy.calculate(
                role.systemCode(),
                Set.of(role.id()),
                repository.hierarchy(role.systemCode()),
                repository.permissions(role.systemCode()));
    }

    @Transactional
    public Role create(TokenProvider.Principal principal, RoleCommand command) {
        String system = command.getSystemCode();
        String code = command.getCode();
        String name = command.getName();
        String description = command.getDescription();
        String identityRole = command.getIdentityRole();
        repository.lockGrants();
        AuthorizationRepository.Identity actor =
                authorization.require(principal, "rbac:role:edit", true);
        Role role =
                new Role(
                        0, system, code, name, description, true, false, identityRole, 0, Set.of());
        RoleHierarchy graph = new RoleHierarchy(List.of(role), Set.of());
        authorization.upperBound(
                actor, system, policy.calculate(system, Set.of(0L), graph, List.of()));
        Role saved = repository.saveRole(role);
        complete(actor, system, "ROLE_CREATED", saved.code());
        return saved;
    }

    @Transactional
    public Role rename(
            TokenProvider.Principal principal,
            long id,
            RoleUpdateCommand command,
            boolean descriptionPresent) {
        String name = command.getName();
        String description = command.getDescription();
        Long version = command.getVersion();
        repository.lockGrants();
        AuthorizationRepository.Identity actor =
                authorization.require(principal, "rbac:role:edit", true);
        Role role = repository.role(id);
        authorization.requireRoleManagement(actor, role);
        DomainException.require(
                version == null || version == role.version(), 409, "VERSION_CONFLICT");
        role.rename(name, descriptionPresent ? description : role.description());
        Role saved = repository.saveRole(role);
        complete(actor, role.systemCode(), "ROLE_UPDATED", role.code());
        return saved;
    }

    @Transactional
    public Role status(TokenProvider.Principal principal, long id, boolean enabled) {
        repository.lockGrants();
        AuthorizationRepository.Identity actor =
                authorization.require(principal, "rbac:role:edit", true);
        Role role = repository.role(id);
        authorization.requireRoleManagement(actor, role);
        role.changeStatus(enabled);
        if (enabled) {
            RoleHierarchy original = repository.hierarchy(role.systemCode());
            List<Role> updated = new ArrayList<>(original.roles());
            updated.removeIf(r -> r.id() == id);
            updated.add(role);
            authorization.upperBound(
                    actor,
                    role.systemCode(),
                    policy.calculate(
                            role.systemCode(),
                            Set.of(id),
                            new RoleHierarchy(updated, original.edges()),
                            repository.permissions(role.systemCode())));
        }
        Role saved = repository.saveRole(role);
        complete(actor, role.systemCode(), "ROLE_STATUS_CHANGED", role.code());
        return saved;
    }

    @Transactional
    public Role permissions(TokenProvider.Principal principal, long id, Set<Long> ids) {
        repository.lockGrants();
        AuthorizationRepository.Identity actor =
                authorization.require(principal, "rbac:role:edit", true);
        Role role = repository.role(id);
        authorization.requireRoleManagement(actor, role);
        List<Permission> all = repository.permissions(role.systemCode());
        List<Permission> selected = all.stream().filter(p -> ids.contains(p.id())).toList();
        DomainException.require(selected.size() == ids.size(), 404, "PERMISSION_NOT_FOUND");
        role.replacePermissions(selected);
        RoleHierarchy graph = repository.hierarchy(role.systemCode());
        List<Role> updated = new ArrayList<>(graph.roles());
        updated.removeIf(r -> r.id() == id);
        updated.add(role);
        authorization.upperBound(
                actor,
                role.systemCode(),
                policy.calculate(
                        role.systemCode(),
                        Set.of(id),
                        new RoleHierarchy(updated, graph.edges()),
                        all));
        Role saved = repository.saveRole(role);
        complete(actor, role.systemCode(), "ROLE_PERMISSIONS_CHANGED", role.code());
        return saved;
    }

    @Transactional
    public void link(TokenProvider.Principal principal, long senior, long junior, boolean add) {
        repository.lockGrants();
        AuthorizationRepository.Identity actor =
                authorization.require(principal, "rbac:role:edit", true);
        Role role = repository.role(senior);
        Role other = repository.role(junior);
        authorization.requireRoleManagement(actor, role);
        authorization.requireRoleManagement(actor, other);
        DomainException.require(
                role.systemCode().equals(other.systemCode()), 409, "CROSS_SYSTEM_ROLE");
        RoleHierarchy graph = repository.hierarchy(role.systemCode());
        if (add) graph.link(senior, junior);
        else graph.unlink(senior, junior);
        if (add) authorization.requireGraphGrant(actor, graph, senior);
        if (add)
            authorization.upperBound(
                    actor,
                    role.systemCode(),
                    policy.calculate(
                            role.systemCode(),
                            Set.of(senior),
                            graph,
                            repository.permissions(role.systemCode())));
        repository.saveEdges(role.systemCode(), graph.edges());
        complete(
                actor,
                role.systemCode(),
                add ? "ROLE_INHERITANCE_ADDED" : "ROLE_INHERITANCE_REMOVED",
                role.code());
    }

    private void complete(
            AuthorizationRepository.Identity actor, String system, String type, String target) {
        integrity.requireAdministrator();
        repository.advanceVersion();
        audits.local(actor, system, type, target, "SUCCESS", null);
    }
}
