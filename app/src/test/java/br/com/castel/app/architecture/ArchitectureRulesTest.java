package br.com.castel.app.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Runs every architecture rule against the real production code of the project.
 *
 * <p>This is the suite that actually gates {@code ./mvnw clean install}. Test code is excluded
 * from the imported classes, so fixtures under {@code architecture.violations} (which live in
 * this same test source root) never leak into these assertions; see
 * {@link ArchitectureViolationProofsTest} for the proof that each rule fails on a violation.
 */
class ArchitectureRulesTest {

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ArchitectureRules.BASE_PACKAGE);
    }

    @Test
    @DisplayName("A1: a domain module only depends on another module's api package")
    void domainModulesOnlyUseEachOthersApi() {
        assertThatCode(() -> ArchitectureRules.checkDomainModulesOnlyUseEachOthersApi(
                        productionClasses, ArchitectureRules.BASE_PACKAGE, ArchitectureRules.DOMAIN_MODULES))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A2: a module only depends on the modules allowed by the technical plan's graph")
    void modulesOnlyDependOnAllowedModules() {
        assertThatCode(() -> ArchitectureRules.checkModulesOnlyDependOnAllowedModules(
                        productionClasses, ArchitectureRules.BASE_PACKAGE, ArchitectureRules.ALLOWED_MODULE_GRAPH))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A5: a module's application layer does not depend on its own infra layer")
    void applicationDoesNotDependOnInfraOfSameModule() {
        assertThatCode(() -> ArchitectureRules.checkApplicationDoesNotDependOnInfraOfSameModule(
                        productionClasses, ArchitectureRules.BASE_PACKAGE, ArchitectureRules.DOMAIN_MODULES))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A3: shared-kernel does not depend on any other module of the project")
    void sharedKernelDoesNotDependOnProjectModules() {
        ArchitectureRules.sharedKernelMustNotDependOnProjectModules(
                        ArchitectureRules.SHARED_KERNEL_PACKAGE, ArchitectureRules.BASE_PACKAGE)
                .check(productionClasses);
    }

    /**
     * Also the proof that {@code jakarta.persistence} is allowed in {@code shared-kernel}: the real
     * module carries {@code AuditedEntity} with {@code @MappedSuperclass} and this rule passes.
     */
    @Test
    @DisplayName("A4: shared-kernel does not depend on Spring or Hibernate")
    void sharedKernelDoesNotDependOnSpringOrHibernate() {
        ArchitectureRules.sharedKernelMustNotDependOnSpringOrHibernate(ArchitectureRules.SHARED_KERNEL_PACKAGE)
                .check(productionClasses);
    }

    @Test
    @DisplayName("B1: an @Entity never exposes a public setter")
    void entitiesMustNotHavePublicSetters() {
        ArchitectureRules.ENTITIES_MUST_NOT_HAVE_PUBLIC_SETTERS.check(productionClasses);
    }

    @Test
    @DisplayName("B2: no field is annotated with @Autowired")
    void noFieldInjection() {
        ArchitectureRules.NO_FIELD_INJECTION.check(productionClasses);
    }

    @Test
    @DisplayName("B3: every EntityId validates its value in the constructor")
    void entityIdMustValidateInConstructor() {
        ArchitectureRules.ENTITY_ID_MUST_VALIDATE_IN_CONSTRUCTOR.check(productionClasses);
    }

    @Test
    @DisplayName("C1: no use of java.util.Date or java.util.Calendar")
    void noJavaUtilDateOrCalendar() {
        ArchitectureRules.NO_JAVA_UTIL_DATE_OR_CALENDAR.check(productionClasses);
    }

    @Test
    @DisplayName("C2: no double or float field in domain scope")
    void noDoubleOrFloatFieldsInDomainScope() {
        ArchitectureRules.noDoubleOrFloatFieldsInDomainScope(ArchitectureRules.SHARED_KERNEL_PACKAGE)
                .check(productionClasses);
    }

    @Test
    @DisplayName("C3: no class named Manager/Helper/Util/Data/Info")
    void noForbiddenClassNameSuffixes() {
        ArchitectureRules.NO_FORBIDDEN_CLASS_NAME_SUFFIXES.check(productionClasses);
    }

    @Test
    @DisplayName("C4: a controller does not depend directly on infrastructure or a repository")
    void controllersMustNotDependOnRepositoryOrInfra() {
        ArchitectureRules.CONTROLLERS_MUST_NOT_DEPEND_ON_REPOSITORY_OR_INFRA.check(productionClasses);
    }

    @Test
    @DisplayName("C5: no IllegalArgumentException thrown in domain scope")
    void noIllegalArgumentExceptionInDomainScope() {
        ArchitectureRules.noIllegalArgumentExceptionInDomainScope(ArchitectureRules.SHARED_KERNEL_PACKAGE)
                .check(productionClasses);
    }

    @Test
    @DisplayName("D1: app does not contain JPA entities")
    void appMustNotContainEntities() {
        ArchitectureRules.APP_MUST_NOT_CONTAIN_ENTITIES.check(productionClasses);
    }

    @Test
    @DisplayName("D2: app does not contain use cases or services")
    void appMustNotContainUseCasesOrServices() {
        ArchitectureRules.APP_MUST_NOT_CONTAIN_USE_CASES_OR_SERVICES.check(productionClasses);
    }
}
