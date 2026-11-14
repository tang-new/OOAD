package com.xmu.rbac.ddd.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.xmu.rbac.ddd.architecture.fixture.StructuredDependent;

import org.junit.jupiter.api.Test;

/** 验证当前骨架与结构化实现隔离，并用测试专用反例证明规则确实会拒绝违规依赖。 领域包尚无业务类型，本测试不以空领域规则通过来宣称已实现 DDD。 */
class BackendIsolationTest {

    private static final ArchRule NO_STRUCTURED_DEPENDENCY =
            noClasses()
                    .that()
                    .resideInAPackage("com.xmu.rbac.ddd..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.xmu.rbac.structured..")
                    .because("两个后端只能共享契约，不得复用 structured 实现");

    @Test
    void productionSkeletonDoesNotDependOnStructured() {
        JavaClasses production =
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.xmu.rbac.ddd");
        // 确认确实扫描了生产类，而不是依靠空输入通过。
        assertThat(production).isNotEmpty();
        NO_STRUCTURED_DEPENDENCY.check(production);
    }

    @Test
    void sameRuleRejectsDeliberatelyForbiddenDependency() {
        JavaClasses counterexample =
                new ClassFileImporter().importClasses(StructuredDependent.class);
        assertThat(NO_STRUCTURED_DEPENDENCY.evaluate(counterexample).hasViolation()).isTrue();
        assertThatThrownBy(() -> NO_STRUCTURED_DEPENDENCY.check(counterexample))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("StructuredDependent")
                .hasMessageContaining("com.xmu.rbac.structured.archfixture.StructuredMarker");
    }
}
