package com.xmu.tools.clonefactory;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CloneFactoryTest {
    @Test
    void copyJavaBeanFieldsAndExcludeInboundSensitiveField() {
        RequestBean request = new RequestBean();
        request.setId(42L);
        request.setName("Alice");
        request.setAge(21);
        request.setEnabled(true);
        request.setPassword("untrusted-password");
        Domain domain = new Domain();
        domain.setPassword("trusted-password");
        assertSame(domain, CloneFactory.copy(domain, request));
        assertEquals(42L, domain.getId());
        assertEquals("Alice", domain.getName());
        assertEquals(21, domain.getAge());
        assertEquals(Boolean.TRUE, domain.getEnabled());
        assertEquals("trusted-password", domain.getPassword());
    }

    @Test
    void excludeOutboundSensitiveFieldEvenWhenTargetAlreadyHasValue() {
        Domain domain = populatedDomain();
        PublicView view = new PublicView();
        view.setPassword("sentinel");
        assertSame(view, CloneFactory.copy(view, domain));
        assertEquals(domain.getId(), view.getId());
        assertEquals(domain.getName(), view.getName());
        assertEquals(domain.getAge(), view.getAge());
        assertEquals(domain.getEnabled(), view.getEnabled());
        assertEquals("sentinel", view.getPassword());
        assertNull(CloneFactory.copy(new PublicView(), domain).getPassword());
    }

    @Test
    void copyNotNullFromPreservesNullsButCopiesZeroAndFalse() {
        Domain domain = populatedDomain();
        PatchBean patch = new PatchBean();
        patch.setAge(0);
        patch.setEnabled(false);
        patch.setPassword("must-not-copy");
        assertSame(domain, CloneFactory.copyNotNull(domain, patch));
        assertEquals(7L, domain.getId());
        assertEquals("original", domain.getName());
        assertEquals(0, domain.getAge());
        assertEquals(Boolean.FALSE, domain.getEnabled());
        assertEquals("secret", domain.getPassword());
        patch.setId(8L);
        patch.setName("updated");
        patch.setAge(null);
        patch.setEnabled(null);
        CloneFactory.copyNotNull(domain, patch);
        assertEquals(8L, domain.getId());
        assertEquals("updated", domain.getName());
        assertEquals(0, domain.getAge());
        assertEquals(Boolean.FALSE, domain.getEnabled());
    }

    @Test
    void copyNotNullToPreservesExistingValuesAndCopiesNonNullWrappers() {
        StoredBean stored = CloneFactory.copy(new StoredBean(), populatedDomain());
        Domain partial = new Domain();
        partial.setAge(0);
        partial.setEnabled(false);
        assertSame(stored, CloneFactory.copyNotNull(stored, partial));
        assertEquals(7L, stored.getId());
        assertEquals("original", stored.getName());
        assertEquals("secret", stored.getPassword());
        assertEquals(0, stored.getAge());
        assertEquals(Boolean.FALSE, stored.getEnabled());
    }

    @Test
    void domainPersistenceRoundtripPreservesEveryFieldWithoutAliasing() {
        Domain original = populatedDomain();
        StoredBean stored = CloneFactory.copy(new StoredBean(), original);
        Domain restored = CloneFactory.copy(new Domain(), stored);
        assertNotSame(original, restored);
        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getAge(), restored.getAge());
        assertEquals(original.getEnabled(), restored.getEnabled());
        assertEquals(original.getPassword(), restored.getPassword());
        stored.setName("changed");
        assertEquals("original", original.getName());
        assertEquals("original", restored.getName());
    }

    @Test
    void ordinaryCopyOverwritesWithNullUnlikeCopyNotNull() {
        Domain domain = populatedDomain();
        CloneFactory.copy(domain, new StoredBean());
        assertNull(domain.getId());
        assertNull(domain.getName());
        assertNull(domain.getAge());
        assertNull(domain.getEnabled());
        assertNull(domain.getPassword());
    }

    private static Domain populatedDomain() {
        Domain domain = new Domain();
        domain.setId(7L);
        domain.setName("original");
        domain.setAge(30);
        domain.setEnabled(true);
        domain.setPassword("secret");
        return domain;
    }
}
