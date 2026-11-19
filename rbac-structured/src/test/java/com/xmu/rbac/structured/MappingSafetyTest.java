package com.xmu.rbac.structured;
import static org.assertj.core.api.Assertions.*;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.xmu.rbac.structured.dto.*;
import org.junit.jupiter.api.Test;
/** 验证教师注解处理器实际生成的copy白名单，不使用仿冒映射器。 */
class MappingSafetyTest {
    /** 创建用户只转移公开资料，保留服务设置的身份、部门与密码哈希。 */
    @Test
    void recordToPublicFieldsKeepsTrustedUserFields() {
        Inputs.UserCreate source = new Inputs.UserCreate("teacher", "教师", "T001", null, 2L, "initial-password");
        com.xmu.rbac.structured.entity.User target = new com.xmu.rbac.structured.entity.User();
        target.departmentId = 3L;
        target.subject = "trusted-subject";
        target.passwordHash = "trusted-hash";
        assertThat(CloneFactory.copy(target, source)).isSameAs(target);
        assertThat(target.username).isEqualTo("teacher");
        assertThat(target.name).isEqualTo("教师");
        assertThat(target.no).isEqualTo("T001");
        assertThat(target.departmentId).isEqualTo(3L);
        assertThat(target.subject).isEqualTo("trusted-subject");
        assertThat(target.passwordHash).isEqualTo("trusted-hash");
    }

    /** 重命名后模板中的可信标识保持原值。 */
    @Test
    void matchingRecordFieldsCopyAndPreserveTrustedIdentity() {
        DisplayName source = new DisplayName("安全名称");
        NameChange target = new NameChange("原名称", 42L, 7L);
        NameChange actual = CloneFactory.copy(target, source);
        assertThat(actual).isNotSameAs(target);
        assertThat(actual.name()).isEqualTo("安全名称");
        assertThat(actual.id()).isEqualTo(42L);
        assertThat(actual.actorId()).isEqualTo(7L);
        assertThat(NameChange.class.getDeclaredFields())
            .extracting(java.lang.reflect.Field::getName)
            .doesNotContain("password", "token", "passwordHash");
    }
}
