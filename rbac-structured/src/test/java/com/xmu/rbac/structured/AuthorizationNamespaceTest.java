package com.xmu.rbac.structured;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.rbac.structured.entity.*;
import com.xmu.rbac.structured.mapper.Store;
import com.xmu.rbac.structured.service.AuthorizationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** 不同独立测试库即使用户ID与全局版本相同，也不能复用Redis授权来源。 */
class AuthorizationNamespaceTest {
    @Test
    @SuppressWarnings("unchecked")
    void separatesSnapshotsWithIdenticalIdsAndVersions() throws Exception {
        Store store = mock(Store.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        User user = new User(); user.id = 42L; user.status = "ACTIVE";
        SoftwareSystem system = new SoftwareSystem(); system.id = 2L; system.code = "course"; system.status = "ENABLED";
        GrantVersion version = new GrantVersion(); version.id = 1L; version.version = 7L;
        when(store.get(User.class, 42L)).thenReturn(user);
        when(store.get(SoftwareSystem.class, 2L)).thenReturn(system);
        when(store.get(GrantVersion.class, 1L)).thenReturn(version);
        when(store.one(SoftwareSystem.class, "e.code=?1", "course")).thenReturn(system);
        ObjectMapper json = new ObjectMapper();
        AuthorizationService.Snapshot previous = new AuthorizationService.Snapshot(List.of(999L), List.of(999L), List.of(), List.of());
        when(values.get("first:authz:course:42:7")).thenReturn(json.writeValueAsString(previous));
        AuthorizationService first = new AuthorizationService(store, redis, json, "first");
        AuthorizationService second = new AuthorizationService(store, redis, json, "second");
        assertThat(first.snapshot(42L, "course").roles()).containsExactly(999L);
        assertThat(second.snapshot(42L, "course").roles()).isEmpty();
        verify(values).get("first:authz:course:42:7");
        verify(values).get("second:authz:course:42:7");
    }

    @Test
    void rejectsBlankNamespace() {
        assertThatThrownBy(() -> new AuthorizationService(mock(Store.class), mock(StringRedisTemplate.class), new ObjectMapper(), " "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
