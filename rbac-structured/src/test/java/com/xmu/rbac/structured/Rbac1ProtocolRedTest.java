package com.xmu.rbac.structured;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
/** 冻结合同回归：根认证路由不能被全局 context-path 隐藏。 */
class Rbac1ProtocolRedTest {
    @Test
    void rootIdentityRoutesAreNotHidden() throws Exception {
        assertThat(Files.readString(Path.of("src/main/resources/application.yml")))
            .doesNotContain("context-path:");
    }
}
