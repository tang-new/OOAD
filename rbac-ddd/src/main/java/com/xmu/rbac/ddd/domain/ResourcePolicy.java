package com.xmu.rbac.ddd.domain;

import com.xmu.rbac.ddd.domain.port.ResourceRepository.*;

import java.util.*;

/** 菜单树与按钮、显式映射的局部一致性规则，不把可见菜单当 API 授权。 */
public final class ResourcePolicy {
    public void validate(Ui proposed, List<Ui> all) {
        DomainException.require(
                proposed.type() != null && Set.of("MENU", "BUTTON").contains(proposed.type()),
                400,
                "INVALID_UI_TYPE");
        DomainException.require(
                !proposed.type().equals("BUTTON")
                        || proposed.permissionCode() != null
                                && !proposed.permissionCode().isBlank(),
                400,
                "BUTTON_PERMISSION_REQUIRED");
        if (proposed.parentId() != null) {
            Ui parent =
                    all.stream()
                            .filter(r -> r.id().equals(proposed.parentId()))
                            .findFirst()
                            .orElseThrow(() -> new DomainException(404, "UI_PARENT_NOT_FOUND"));
            DomainException.require(
                    parent.systemCode().equals(proposed.systemCode())
                            && parent.type().equals("MENU"),
                    409,
                    "INVALID_UI_PARENT");
            Set<Long> visited = new HashSet<>();
            Long id = proposed.parentId();
            while (id != null) {
                DomainException.require(
                        !id.equals(proposed.id()) && visited.add(id), 409, "UI_CYCLE");
                Long current = id;
                Ui node = all.stream().filter(r -> r.id().equals(current)).findFirst().orElse(null);
                id = node == null ? null : node.parentId();
            }
        } else DomainException.require(proposed.type().equals("MENU"), 409, "BUTTON_REQUIRES_MENU");
        DomainException.require(
                !proposed.type().equals("BUTTON")
                        || all.stream()
                                .noneMatch(
                                        r ->
                                                r.parentId() != null
                                                        && r.parentId().equals(proposed.id())),
                409,
                "UI_HAS_CHILDREN");
    }

    public void requireDeletable(Ui resource, List<Ui> all, List<Mapping> mappings) {
        DomainException.require(
                all.stream().noneMatch(r -> Objects.equals(r.parentId(), resource.id()))
                        && mappings.stream().noneMatch(m -> m.uiResourceId() == resource.id()),
                409,
                "UI_RESOURCE_IN_USE");
    }

    public void validateMapping(String system, Ui ui, ApiOperation api) {
        DomainException.require(
                system.equals(ui.systemCode()) && system.equals(api.systemCode()),
                409,
                "CROSS_SYSTEM_MAPPING");
    }

    public void requirePermission(String system, Permission permission, Permission.Kind kind) {
        DomainException.require(
                permission.systemCode().equals(system) && permission.kind() == kind,
                409,
                "INVALID_RESOURCE_PERMISSION");
    }

    public List<Ui> visible(List<Ui> all, AuthorizationPolicy.Effective effective) {
        Set<Long> included = new HashSet<>();
        for (Ui root : all)
            if (root.parentId() == null) include(root, all, effective, included, new HashSet<>());
        return all.stream().filter(u -> included.contains(u.id())).toList();
    }

    private boolean include(
            Ui node,
            List<Ui> all,
            AuthorizationPolicy.Effective effective,
            Set<Long> included,
            Set<Long> path) {
        if (!node.status().equals("ENABLED") || !path.add(node.id())) return false;
        boolean children = false;
        for (Ui child : all)
            if (node.id().equals(child.parentId()))
                children |= include(child, all, effective, included, new HashSet<>(path));
        boolean own =
                node.permissionCode() == null
                        || effective.has(node.permissionCode(), Permission.Kind.UI);
        boolean visible = own || (node.type().equals("MENU") && children);
        if (visible) included.add(node.id());
        return visible;
    }

    public Ui patch(Ui old, Ui values, Set<String> fields) {
        DomainException.require(
                (!fields.contains("type") || values.type() != null)
                        && (!fields.contains("code") || values.code() != null)
                        && (!fields.contains("name") || values.name() != null)
                        && (!fields.contains("sortOrder") || values.sortOrder() != null)
                        && (!fields.contains("status") || values.status() != null)
                        && (!fields.contains("permissionCode") || values.permissionCode() != null),
                400,
                "INVALID_UI_PATCH");
        return new Ui(
                old.id(),
                old.systemCode(),
                fields.contains("parentId") ? values.parentId() : old.parentId(),
                fields.contains("type") ? values.type() : old.type(),
                fields.contains("code") ? values.code() : old.code(),
                fields.contains("name") ? values.name() : old.name(),
                fields.contains("route") ? values.route() : old.route(),
                fields.contains("component") ? values.component() : old.component(),
                fields.contains("sortOrder") ? values.sortOrder() : old.sortOrder(),
                fields.contains("status") ? values.status() : old.status(),
                fields.contains("permissionCode") ? values.permissionCode() : old.permissionCode());
    }
}
