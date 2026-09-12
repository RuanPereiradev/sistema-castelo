package br.com.castel.app.architecture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that every rule in {@link ArchitectureRules} actually fails when violated.
 *
 * <p>Each test runs the exact same rule (method or constant) used by
 * {@link ArchitectureRulesTest} against the production code, but against fixtures under
 * {@code br.com.castel.app.architecture.violations} deliberately written to break it. A rule
 * without a corresponding proof here offers no real protection.
 */
class ArchitectureViolationProofsTest {

    private static final String VIOLATIONS_PACKAGE = "br.com.castel.app.architecture.violations";

    private static final String FAKE_BASE_PACKAGE = VIOLATIONS_PACKAGE + ".modulegraph.fake";

    private static final String FAKE_SHARED_KERNEL_PACKAGE = FAKE_BASE_PACKAGE + ".fakesharedkernel";

    private static final List<String> FAKE_DOMAIN_MODULES =
            List.of("fakehotel", "fakerestaurant", "fakebilling", "fakeidentity", "faketaxinvoice", "fakepayment");

    private static final Map<String, Set<String>> FAKE_ALLOWED_MODULE_GRAPH = Map.of(
            "fakehotel", Set.of("fakebilling"),
            "fakerestaurant", Set.of("fakebilling"),
            "fakebilling", Set.of("faketaxinvoice", "fakepayment"),
            "fakeidentity", Set.of(),
            "faketaxinvoice", Set.of(),
            "fakepayment", Set.of());

    private static JavaClasses violationClasses;

    @BeforeAll
    static void importViolationClasses() {
        violationClasses = new ClassFileImporter().importPackages(VIOLATIONS_PACKAGE);
    }

    @Test
    @DisplayName("A1 fails when a module reaches into another module's domain package directly")
    void domainModulesOnlyUseEachOthersApiFailsOnViolation() {
        assertThatThrownBy(() -> ArchitectureRules.checkDomainModulesOnlyUseEachOthersApi(
                        violationClasses, FAKE_BASE_PACKAGE, FAKE_DOMAIN_MODULES))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("A2 fails when a module depends on another module outside the allowed graph")
    void modulesOnlyDependOnAllowedModulesFailsOnViolation() {
        assertThatThrownBy(() -> ArchitectureRules.checkModulesOnlyDependOnAllowedModules(
                        violationClasses, FAKE_BASE_PACKAGE, FAKE_ALLOWED_MODULE_GRAPH))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("A3 fails when shared-kernel depends on another module of the project")
    void sharedKernelDoesNotDependOnProjectModulesFailsOnViolation() {
        assertThatThrownBy(() -> ArchitectureRules
                        .sharedKernelMustNotDependOnProjectModules(FAKE_SHARED_KERNEL_PACKAGE, FAKE_BASE_PACKAGE)
                        .check(violationClasses))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("A4 fails when shared-kernel depends on Spring")
    void sharedKernelDoesNotDependOnSpringOrJpaFailsOnViolation() {
        assertThatThrownBy(() -> ArchitectureRules
                        .sharedKernelMustNotDependOnSpringOrJpa(FAKE_SHARED_KERNEL_PACKAGE)
                        .check(violationClasses))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("B1 fails when an @Entity exposes a public setter")
    void entitiesMustNotHavePublicSettersFailsOnViolation() {
        assertThatThrownBy(() -> ArchitectureRules.ENTITIES_MUST_NOT_HAVE_PUBLIC_SETTERS.check(violationClasses))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("B2 fails when a field is annotated with @Autowired")
    void noFieldInjectionFailsOnViolation() {
        assertThatThrownBy(() -> ArchitectureRules.NO_FIELD_INJECTION.check(violationClasses))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("B3 fails when an EntityId's constructor never calls requireValid(...)")
    void entityIdMustValidateInConstructorFailsOnViolation() {
        assertThatThrownBy(() -> ArchitectureRules.ENTITY_ID_MUST_VALIDATE_IN_CONSTRUCTOR.check(violationClasses))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("C1 fails when a class uses java.util.Date")
    void noJavaUtilDateOrCalendarFailsOnViolation() {
        assertThatThrownBy(() -> ArchitectureRules.NO_JAVA_UTIL_DATE_OR_CALENDAR.check(violationClasses))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("C2 fails when a domain-scoped class has a double field")
    void noDoubleOrFloatFieldsInDomainScopeFailsOnViolation() {
        assertThatThrownBy(() -> ArchitectureRules
                        .noDoubleOrFloatFieldsInDomainScope(ArchitectureRules.SHARED_KERNEL_PACKAGE)
                        .check(violationClasses))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("C3 fails when a class is named Manager/Helper/Util/Data/Info")
    void noForbiddenClassNameSuffixesFailsOnViolation() {
        assertThatThrownBy(() -> ArchitectureRules.NO_FORBIDDEN_CLASS_NAME_SUFFIXES.check(violationClasses))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("C4 fails when a controller depends directly on a repository")
    void controllersMustNotDependOnRepositoryOrInfraFailsOnViolation() {
        assertThatThrownBy(() ->
                        ArchitectureRules.CONTROLLERS_MUST_NOT_DEPEND_ON_REPOSITORY_OR_INFRA.check(violationClasses))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("C5 fails when a domain-scoped class throws IllegalArgumentException")
    void noIllegalArgumentExceptionInDomainScopeFailsOnViolation() {
        assertThatThrownBy(() -> ArchitectureRules
                        .noIllegalArgumentExceptionInDomainScope(ArchitectureRules.SHARED_KERNEL_PACKAGE)
                        .check(violationClasses))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("D1 fails when app contains a JPA entity")
    void appMustNotContainEntitiesFailsOnViolation() {
        assertThatThrownBy(() -> ArchitectureRules.APP_MUST_NOT_CONTAIN_ENTITIES.check(violationClasses))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("D2 fails when app contains a class named UseCase or Service")
    void appMustNotContainUseCasesOrServicesFailsOnViolation() {
        assertThatThrownBy(
                        () -> ArchitectureRules.APP_MUST_NOT_CONTAIN_USE_CASES_OR_SERVICES.check(violationClasses))
                .isInstanceOf(AssertionError.class);
    }
}
