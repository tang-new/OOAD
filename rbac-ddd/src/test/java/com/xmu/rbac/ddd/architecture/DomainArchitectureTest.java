package com.xmu.rbac.ddd.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import static org.assertj.core.api.Assertions.*;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.*;
import com.tngtech.archunit.lang.ArchRule;

import org.junit.jupiter.api.Test;

/** 领域纯净和依赖倒置规则必须同时通过生产代码并拒绝反例。 */
class DomainArchitectureTest {
    private final ArchRule domain =
            noClasses()
                    .that()
                    .resideInAPackage("..ddd.domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "com.fasterxml.jackson..",
                            "..ddd.infrastructure..",
                            "..ddd.application..",
                            "..ddd.interfaces..");
    private final ArchRule application =
            noClasses()
                    .that()
                    .resideInAPackage("..ddd.application..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "..ddd.infrastructure..",
                            "..ddd.interfaces..",
                            "org.springframework.data.jpa..",
                            "jakarta.persistence..");

    @Test
    void productionRespectsDependencyInversion() {
        JavaClasses production =
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.xmu.rbac.ddd");
        domain.check(production);
        application.check(production);
    }

    @Test
    void forbiddenTechnicalDomainFails() {
        JavaClasses counterexample =
                new ClassFileImporter()
                        .importClasses(com.xmu.rbac.ddd.domain.archfixture.TechnicalDomain.class);
        assertThat(domain.evaluate(counterexample).hasViolation()).isTrue();
        assertThatThrownBy(() -> domain.check(counterexample)).isInstanceOf(AssertionError.class);
    }

    @Test
    void forbiddenApplicationEntityFails() {
        JavaClasses counterexample =
                new ClassFileImporter()
                        .importClasses(
                                com.xmu.rbac.ddd.application.archfixture.PersistenceDependent
                                        .class);
        assertThat(application.evaluate(counterexample).hasViolation()).isTrue();
        assertThatThrownBy(() -> application.check(counterexample))
                .isInstanceOf(AssertionError.class);
    }
}
