package com.xmu.tools.clonefactory;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.xmu.tools.clonefactory.ImmutableMappings.FinalSnapshot;
import com.xmu.tools.clonefactory.ImmutableMappings.Profile;
import com.xmu.tools.clonefactory.ImmutableMappings.PublicFields;
import com.xmu.tools.clonefactory.ImmutableMappings.Snapshot;
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

    /** record 来源可以通过访问器同时写入公共字段及不可变模板。 */
    @Test
    void recordSourceCopiesIntoPublicFieldsAndCanonicalConstructor() {
        Profile source = new Profile("教师", 0, false);
        PublicFields fields = new PublicFields();
        assertSame(fields, CloneFactory.copy(fields, source));
        assertEquals("教师", fields.name);
        assertEquals(0, fields.age);
        assertEquals(Boolean.FALSE, fields.enabled);

        Snapshot template = new Snapshot(42L, "旧值", 21, true);
        Snapshot snapshot = CloneFactory.copy(template, source);
        assertNotSame(template, snapshot);
        assertEquals(new Snapshot(42L, "教师", 0, false), snapshot);
    }

    /** copyNotNull 读取模板回退值，普通 copy 保留显式清空语义。 */
    @Test
    void recordCopyNotNullPreservesTemplateAndCopiesZeroAndFalse() {
        Snapshot template = new Snapshot(42L, "旧值", 21, true);
        Profile patch = new Profile(null, 0, false);
        assertEquals(new Snapshot(42L, "旧值", 0, false), CloneFactory.copyNotNull(template, patch));
        assertEquals(new Snapshot(42L, null, 0, false), CloneFactory.copy(template, patch));
    }

    /** final 字段通过构造器赋值，原模板保持原值。 */
    @Test
    void finalTargetUsesMatchingConstructor() {
        FinalSnapshot template = new FinalSnapshot("旧值", 21);
        FinalSnapshot result = CloneFactory.copy(template, new Profile("新值", 0, false));
        assertNotSame(template, result);
        assertEquals("旧值", template.name);
        assertEquals("新值", result.name);
        assertEquals(0, result.age);
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
