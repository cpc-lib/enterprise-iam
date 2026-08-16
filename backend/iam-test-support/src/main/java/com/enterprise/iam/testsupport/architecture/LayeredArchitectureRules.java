package com.enterprise.iam.testsupport.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Executable baseline for the service layering frozen by SPEC 33 and SPEC 39.
 */
public final class LayeredArchitectureRules {

    private LayeredArchitectureRules() {
    }

    public static void verify(String serviceRootPackage) {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(serviceRootPackage);

        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure..",
                        "org.springframework..",
                        "org.mybatis..",
                        "com.baomidou.mybatisplus..")
                .allowEmptyShould(true)
                .because("domain code must remain framework and adapter independent")
                .check(classes);

        noClasses()
                .that().resideInAnyPackage("..interfaces.rest..", "..controller..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure.persistence.mapper..",
                        "..mapper..")
                .allowEmptyShould(true)
                .because("HTTP adapters must call application services, never mappers")
                .check(classes);

        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure.persistence.mapper..",
                        "..mapper..")
                .allowEmptyShould(true)
                .because("application services depend on domain ports, never persistence mappers")
                .check(classes);
    }
}
