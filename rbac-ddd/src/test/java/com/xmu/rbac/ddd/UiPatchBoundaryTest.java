package com.xmu.rbac.ddd;

import static org.assertj.core.api.Assertions.*;

import cn.edu.xmu.clonefactory.util.CloneFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.rbac.ddd.application.*;
import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.ResourceRepository.Ui;
import com.xmu.rbac.ddd.infrastructure.persistence.*;
import com.xmu.rbac.ddd.infrastructure.persistence.entity.UiResourceEntity;
import com.xmu.rbac.ddd.interfaces.rest.dto.*;

import org.junit.jupiter.api.Test;

import java.util.*;

/** UI PATCH 的 null 与缺省语义及教师生成器的真实三处边界。 */
class UiPatchBoundaryTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Ui old =
            new Ui(7L, "s", 1L, "MENU", "menu", "菜单", "/old", "OldComponent", 4, "ENABLED", "view");

    private Ui apply(String json) throws Exception {
        UiChangeRequest request = mapper.readValue(json, UiChangeRequest.class);
        UiChangeCommand command = CloneFactory.copy(new UiChangeCommand(), request);
        Ui values =
                new Ui(
                        null,
                        null,
                        command.getParentId(),
                        command.getType(),
                        command.getCode(),
                        command.getName(),
                        command.getRoute(),
                        command.getComponent(),
                        command.getSortOrder(),
                        command.getStatus(),
                        command.getPermissionCode());
        return new ResourcePolicy().patch(old, values, request.provided());
    }

    @Test
    void omittedFieldsRemainUnchanged() throws Exception {
        Ui changed = apply("{\"name\":\"新名称\"}");
        assertThat(changed.route()).isEqualTo("/old");
        assertThat(changed.component()).isEqualTo("OldComponent");
        assertThat(changed.parentId()).isEqualTo(1);
        assertThat(changed.name()).isEqualTo("新名称");
        assertThat(changed.systemCode()).isEqualTo("s");
        assertThat(changed.id()).isEqualTo(7);
    }

    @Test
    void explicitNullClearsAllNullableUiFields() throws Exception {
        Ui changed = apply("{\"route\":null,\"component\":null,\"parentId\":null}");
        assertThat(changed.route()).isNull();
        assertThat(changed.component()).isNull();
        assertThat(changed.parentId()).isNull();
        assertThat(changed.permissionCode()).isEqualTo("view");
    }

    @Test
    void explicitNullCannotEraseRequiredNameOrPermission() {
        assertThatThrownBy(() -> apply("{\"name\":null}")).hasMessageContaining("INVALID_UI_PATCH");
        assertThatThrownBy(() -> apply("{\"permissionCode\":null}"))
                .hasMessageContaining("INVALID_UI_PATCH");
    }

    @Test
    void resultToViewUsesActualGeneratedCopy() {
        UiResourceView view = CloneFactory.copy(new UiResourceView(), UiResult.from(old));
        assertThat(view.getId()).isEqualTo(7);
        assertThat(view.getName()).isEqualTo("菜单");
        assertThat(view.getParentId()).isEqualTo(1);
        assertThat(view.getSystemCode()).isEqualTo("s");
    }

    @Test
    void persistenceSnapshotDoesNotOverwriteSensitiveIdentifiers() {
        UiResourceEntity entity = new UiResourceEntity();
        entity.setId(7L);
        entity.setSystemId(2L);
        entity.setVersion(9L);
        entity.setName("菜单");
        entity.setParentId(1L);
        entity.setRoute("/old");
        UiState state = CloneFactory.copy(new UiState(), entity);
        state.setRoute(null);
        CloneFactory.copy(entity, state);
        assertThat(entity.getRoute()).isNull();
        assertThat(entity.getName()).isEqualTo("菜单");
        assertThat(entity.getId()).isEqualTo(7);
        assertThat(entity.getSystemId()).isEqualTo(2);
        assertThat(entity.getVersion()).isEqualTo(9);
    }
}
