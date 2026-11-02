package com.food4good;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private static final String ROOT = "com.food4good.";
    private static final String SHARED = "shared";

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.food4good");

    @Test
    void domainIsPlainJava() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta..", "..infrastructure..")
                .check(classes);
    }

    @Test
    void portsDoNotDependOnServices() {
        noClasses().that().resideInAPackage("..domain.ports..")
                .should().dependOnClassesThat().resideInAPackage("..domain.service..")
                .check(classes);
    }

    @Test
    void modulesReachEachOtherOnlyThroughInboundPortsOrShared() {
        classes().should(reachOtherModulesOnlyThroughInboundPorts()).check(classes);
    }

    private static ArchCondition<JavaClass> reachOtherModulesOnlyThroughInboundPorts() {
        return new ArchCondition<>("reach other modules only through their domain.ports.in") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                var originModule = moduleOf(origin.getPackageName());
                origin.getDirectDependenciesFromSelf().forEach(dependency -> {
                    var target = dependency.getTargetClass().getBaseComponentType().getPackageName();
                    var targetModule = moduleOf(target);
                    if (targetModule.isEmpty() || targetModule.equals(originModule)) {
                        return;
                    }
                    var isShared = targetModule.get().equals(SHARED);
                    var isInboundPort = target.startsWith(ROOT + targetModule.get() + ".domain.ports.in");
                    var fromShared = originModule.filter(SHARED::equals).isPresent();
                    if (fromShared || !(isShared || isInboundPort)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                });
            }
        };
    }

    private static Optional<String> moduleOf(String packageName) {
        if (!packageName.startsWith(ROOT)) {
            return Optional.empty();
        }
        return Optional.of(packageName.substring(ROOT.length()).split("\\.")[0]);
    }
}
