package com.xmu.rbac.structured;
import static org.assertj.core.api.Assertions.*;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.xmu.rbac.structured.dto.*;
import org.junit.jupiter.api.Test;
/** 验证教师注解处理器实际生成的copy白名单，不使用仿冒映射器。 */
class MappingSafetyTest {
    @Test
    void matchingBeanFieldsCopyButIdentityDoesNot() {
        DisplayName source = new DisplayName();
        source.setName("安全名称");
        NameChange target = new NameChange();
        target.setId(42L);
        target.setActorId(7L);
        assertThat(CloneFactory.copy(target, source)).isSameAs(target);
        assertThat(target.getName()).isEqualTo("安全名称");
        assertThat(target.getId()).isEqualTo(42L);
        assertThat(target.getActorId()).isEqualTo(7L);
        assertThat(NameChange.class.getDeclaredFields())
            .extracting(java.lang.reflect.Field::getName)
            .doesNotContain("password", "token", "passwordHash");
    }
}
