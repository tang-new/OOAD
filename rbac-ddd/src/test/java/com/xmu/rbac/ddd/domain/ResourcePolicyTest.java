package com.xmu.rbac.ddd.domain;

import static org.assertj.core.api.Assertions.*;

import com.xmu.rbac.ddd.domain.port.ResourceRepository.*;

import org.junit.jupiter.api.Test;

import java.util.*;

/** 菜单层级和角色 DAG 是两种不同的模型。 */
class ResourcePolicyTest {
    private final ResourcePolicy policy = new ResourcePolicy();

    private Ui ui(Long id, Long parent, String type, String permission, String status) {
        return new Ui(id, "s", parent, type, "r" + id, "资源", null, null, 0, status, permission);
    }

    private AuthorizationPolicy.Effective effective(String... permissions) {
        List<Permission> ps = new ArrayList<>();
        for (int i = 0; i < permissions.length; i++)
            ps.add(new Permission(i, "s", permissions[i], "权限", Permission.Kind.UI, true));
        return new AuthorizationPolicy.Effective(List.of(), List.of(), ps, List.of());
    }

    @Test
    void parentMenuDoesNotGrantButton() {
        Ui menu = ui(1L, null, "MENU", "view", "ENABLED");
        Ui button = ui(2L, 1L, "BUTTON", "edit", "ENABLED");
        assertThat(policy.visible(List.of(menu, button), effective("view"))).containsExactly(menu);
    }

    @Test
    void authorizedChildKeepsAncestorContainer() {
        Ui menu = ui(1L, null, "MENU", "view", "ENABLED");
        Ui button = ui(2L, 1L, "BUTTON", "edit", "ENABLED");
        assertThat(policy.visible(List.of(menu, button), effective("edit")))
                .containsExactly(menu, button);
    }

    @Test
    void disabledParentCutsChildren() {
        Ui menu = ui(1L, null, "MENU", "view", "DISABLED");
        Ui button = ui(2L, 1L, "BUTTON", "edit", "ENABLED");
        assertThat(policy.visible(List.of(menu, button), effective("edit"))).isEmpty();
    }

    @Test
    void buttonRequiresMenuParent() {
        assertThatThrownBy(
                        () -> policy.validate(ui(2L, null, "BUTTON", "edit", "ENABLED"), List.of()))
                .hasMessageContaining("BUTTON_REQUIRES_MENU");
    }

    @Test
    void buttonsCannotParentOtherNodes() {
        Ui button = ui(1L, null, "BUTTON", "edit", "ENABLED");
        assertThatThrownBy(
                        () ->
                                policy.validate(
                                        ui(2L, 1L, "BUTTON", "edit", "ENABLED"), List.of(button)))
                .hasMessageContaining("INVALID_UI_PARENT");
    }

    @Test
    void selfCycleRejected() {
        Ui existing = ui(1L, null, "MENU", "view", "ENABLED");
        assertThatThrownBy(
                        () ->
                                policy.validate(
                                        ui(1L, 1L, "MENU", "view", "ENABLED"), List.of(existing)))
                .hasMessageContaining("UI_CYCLE");
    }

    @Test
    void transitiveCycleRejected() {
        Ui a = ui(1L, null, "MENU", "view", "ENABLED"), b = ui(2L, 1L, "MENU", "view", "ENABLED");
        assertThatThrownBy(
                        () -> policy.validate(ui(1L, 2L, "MENU", "view", "ENABLED"), List.of(a, b)))
                .hasMessageContaining("UI_CYCLE");
    }

    @Test
    void mappedUiCannotBeDeleted() {
        Ui ui = ui(1L, null, "MENU", "view", "ENABLED");
        assertThatThrownBy(
                        () -> policy.requireDeletable(ui, List.of(ui), List.of(new Mapping(1, 2))))
                .hasMessageContaining("UI_RESOURCE_IN_USE");
    }

    @Test
    void parentsWithChildrenCannotBeDeleted() {
        Ui a = ui(1L, null, "MENU", "view", "ENABLED"), b = ui(2L, 1L, "BUTTON", "edit", "ENABLED");
        assertThatThrownBy(() -> policy.requireDeletable(a, List.of(a, b), List.of()))
                .hasMessageContaining("UI_RESOURCE_IN_USE");
    }

    @Test
    void mappingNeverCrossesSystems() {
        assertThatThrownBy(
                        () ->
                                policy.validateMapping(
                                        "s",
                                        ui(1L, null, "MENU", "view", "ENABLED"),
                                        new ApiOperation(
                                                2,
                                                "other",
                                                "op",
                                                "GET",
                                                "/op",
                                                null,
                                                null,
                                                ApiOperation.Policy.PUBLIC,
                                                true)))
                .hasMessageContaining("CROSS_SYSTEM_MAPPING");
    }

    @Test
    void apiCannotUseUiPermission() {
        assertThatThrownBy(
                        () ->
                                policy.requirePermission(
                                        "s",
                                        new Permission(
                                                1, "s", "view", "P", Permission.Kind.UI, true),
                                        Permission.Kind.API))
                .hasMessageContaining("INVALID_RESOURCE_PERMISSION");
    }
}
