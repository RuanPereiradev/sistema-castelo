package br.com.castel.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import br.com.castel.sharedkernel.EntityId;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reusable ArchUnit rule logic for the whole project.
 *
 * <p>Every rule that depends on a fixed set of packages (module names, the shared-kernel
 * package, the app package) is extracted into a method or constant so that the exact same
 * logic can be exercised twice: once against the real production code in
 * {@link ArchitectureRulesTest}, and once against synthetic fixtures under
 * {@code br.com.castel.app.architecture.violations} in {@link ArchitectureViolationProofsTest},
 * proving that a violation actually fails the rule.
 */
final class ArchitectureRules {

    static final String BASE_PACKAGE = "br.com.castel";
    static final String SHARED_KERNEL_PACKAGE = "br.com.castel.sharedkernel";
    static final String APP_PACKAGE = "br.com.castel.app";

    /** Real domain modules, restricted by the module boundary rules (app is exempt). */
    static final List<String> DOMAIN_MODULES =
            List.of("hotel", "restaurant", "billing", "identity", "taxinvoice", "payment");

    /**
     * The dependency graph allowed by the technical plan (section 4): a module may freely
     * depend on itself and on shared-kernel; anything else must be listed here explicitly.
     */
    static final Map<String, Set<String>> ALLOWED_MODULE_GRAPH = Map.of(
            "hotel", Set.of("billing"),
            "restaurant", Set.of("billing"),
            "billing", Set.of("taxinvoice", "payment"),
            "identity", Set.of(),
            "taxinvoice", Set.of(),
            "payment", Set.of());

    private ArchitectureRules() {
    }

    // ---------------------------------------------------------------------------------------
    // A1 - a domain module may only depend on another module's api package, never its
    // domain/application/infra packages.
    // ---------------------------------------------------------------------------------------

    static void checkDomainModulesOnlyUseEachOthersApi(
            JavaClasses classes, String basePackage, List<String> domainModules) {
        for (String module : domainModules) {
            for (String other : domainModules) {
                if (module.equals(other)) {
                    continue;
                }
                DescribedPredicate<JavaClass> otherModuleInternals =
                        JavaClass.Predicates.resideInAPackage(basePackage + "." + other + ".domain..")
                                .or(JavaClass.Predicates.resideInAPackage(basePackage + "." + other + ".application.."))
                                .or(JavaClass.Predicates.resideInAPackage(basePackage + "." + other + ".infra.."));
                ArchRule rule = noClasses()
                        .that().resideInAPackage(basePackage + "." + module + "..")
                        .should().dependOnClassesThat(otherModuleInternals)
                        .because("module '" + module + "' may only depend on the api package of module '" + other
                                + "', never its domain, application or infra packages");
                rule.check(classes);
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // A5 - inside a module, the application layer depends on ports, never on the infra layer
    // that implements them.
    // ---------------------------------------------------------------------------------------

    // Modules without an application or infra package yet (hotel, restaurant, ...) leave the
    // rule with nothing to check, hence allowEmptyShould.
    static void checkApplicationDoesNotDependOnInfraOfSameModule(
            JavaClasses classes, String basePackage, List<String> modules) {
        for (String module : modules) {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(basePackage + "." + module + ".application..")
                    .should().dependOnClassesThat().resideInAPackage(basePackage + "." + module + ".infra..")
                    .because("the application layer of module '" + module + "' depends on ports it declares "
                            + "(in application or domain), never on the infra classes that implement them")
                    .allowEmptyShould(true);
            rule.check(classes);
        }
    }

    // ---------------------------------------------------------------------------------------
    // A2 - a module may only depend on the modules listed as allowed targets in its graph
    // entry (shared-kernel and the module itself are always allowed, because they are never
    // part of the iterated module list).
    // ---------------------------------------------------------------------------------------

    static void checkModulesOnlyDependOnAllowedModules(
            JavaClasses classes, String basePackage, Map<String, Set<String>> allowedGraph) {
        for (String module : allowedGraph.keySet()) {
            Set<String> allowedTargets = allowedGraph.get(module);
            for (String other : allowedGraph.keySet()) {
                if (other.equals(module) || allowedTargets.contains(other)) {
                    continue;
                }
                ArchRule rule = noClasses()
                        .that().resideInAPackage(basePackage + "." + module + "..")
                        .should().dependOnClassesThat().resideInAPackage(basePackage + "." + other + "..")
                        .because("module '" + module + "' is not allowed to depend on module '" + other + "'");
                rule.check(classes);
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // A3 - shared-kernel must not depend on any other module of the project.
    // ---------------------------------------------------------------------------------------

    static ArchRule sharedKernelMustNotDependOnProjectModules(String sharedKernelPackage, String basePackage) {
        return noClasses()
                .that().resideInAPackage(sharedKernelPackage + "..")
                .should().dependOnClassesThat(
                        JavaClass.Predicates.resideInAPackage(basePackage + "..")
                                .and(DescribedPredicate.not(JavaClass.Predicates.resideInAPackage(sharedKernelPackage + ".."))))
                .because("shared-kernel must not depend on any other module of the project");
    }

    // ---------------------------------------------------------------------------------------
    // A4 - shared-kernel stays free of framework code: no Spring and no Hibernate. The
    // jakarta.persistence mapping annotations are allowed, and only them, so the audit
    // superclass can be declared once for every module (decision #30 of task 0.5b).
    // ---------------------------------------------------------------------------------------

    /**
     * {@code jakarta.persistence} is deliberately absent from the forbidden list. That it is really
     * allowed is not proved by a fixture but by the production run of this rule: the real
     * {@code shared-kernel} carries {@code AuditedEntity}, annotated with {@code @MappedSuperclass},
     * and the rule passes over it.
     */
    static ArchRule sharedKernelMustNotDependOnSpringOrHibernate(String sharedKernelPackage) {
        return noClasses()
                .that().resideInAPackage(sharedKernelPackage + "..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "org.hibernate..")
                .because("shared-kernel must stay free of framework code: the JPA mapping annotations are "
                        + "allowed there, the persistence provider and the container are not");
    }

    // ---------------------------------------------------------------------------------------
    // B1 - an @Entity never exposes a public setter; state changes through named business
    // methods.
    // ---------------------------------------------------------------------------------------

    // identity.domain.User is a real @Entity now, so this rule runs a non-empty check in
    // production. allowEmptyShould stays as a safeguard for modules (hotel, restaurant, billing,
    // ...) that don't have an @Entity yet, so the rule doesn't start failing the moment one of
    // them does before another gets one.
    static final ArchRule ENTITIES_MUST_NOT_HAVE_PUBLIC_SETTERS = noMethods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
            .and().arePublic()
            .should().haveNameMatching("set[A-Z].*")
            .because("entities change state through named business methods, never through public setters")
            .allowEmptyShould(true);

    // ---------------------------------------------------------------------------------------
    // B2 - no field injection; dependencies are injected through the constructor.
    // ---------------------------------------------------------------------------------------

    static final ArchRule NO_FIELD_INJECTION = noFields()
            .should().beAnnotatedWith(Autowired.class)
            .because("dependencies are injected through the constructor, never through field injection");

    // ---------------------------------------------------------------------------------------
    // B3 - every EntityId implementation guards its value through EntityId.requireValid(...)
    // from its (compact) constructor.
    // ---------------------------------------------------------------------------------------

    private static final ArchCondition<JavaClass> CALL_ENTITY_ID_REQUIRE_VALID_FROM_CONSTRUCTOR =
            new ArchCondition<>("call EntityId.requireValid(...) from its constructor") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    boolean callsRequireValid = javaClass.getConstructors().stream()
                            .flatMap(constructor -> constructor.getCallsFromSelf().stream())
                            .anyMatch(ArchitectureRules::isCallToEntityIdRequireValid);
                    String message = javaClass.getFullName()
                            + " does not call EntityId.requireValid(...) from its constructor";
                    events.add(new SimpleConditionEvent(javaClass, callsRequireValid, message));
                }
            };

    private static boolean isCallToEntityIdRequireValid(JavaCall<?> call) {
        return call.getName().equals("requireValid") && call.getTargetOwner().isAssignableTo(EntityId.class);
    }

    // identity.api.UserId is a real EntityId record now, so this rule runs a non-empty check
    // in production. allowEmptyShould stays as a safeguard for modules that don't have a
    // concrete EntityId yet.
    static final ArchRule ENTITY_ID_MUST_VALIDATE_IN_CONSTRUCTOR = classes()
            .that().implement(EntityId.class)
            .should(CALL_ENTITY_ID_REQUIRE_VALID_FROM_CONSTRUCTOR)
            .because("every EntityId must guard its value through EntityId.requireValid(...)")
            .allowEmptyShould(true);

    // ---------------------------------------------------------------------------------------
    // C1 - java.time replaces java.util.Date and java.util.Calendar.
    // ---------------------------------------------------------------------------------------

    /**
     * {@code br.com.castel.identity.infra.JwtTokenIssuer} is a narrow, documented exception: the
     * jjwt 0.13 builder/parser API ({@code JwtBuilder#issuedAt}, {@code #expiration},
     * {@code io.jsonwebtoken.Clock#now}) only accepts {@code java.util.Date}, with no
     * {@code java.time} overload. It converts from {@code Instant}/{@code Clock} at that single
     * boundary and never leaks {@code java.util.Date} beyond it.
     */
    private static final String JWT_DATE_BOUNDARY_CLASS = "br.com.castel.identity.infra.JwtTokenIssuer";

    static final ArchRule NO_JAVA_UTIL_DATE_OR_CALENDAR = noClasses()
            .that().doNotHaveFullyQualifiedName(JWT_DATE_BOUNDARY_CLASS)
            .should().dependOnClassesThat().belongToAnyOf(Date.class, Calendar.class)
            .because("java.time replaces java.util.Date and java.util.Calendar throughout the project, "
                    + "except " + JWT_DATE_BOUNDARY_CLASS + ", where the jjwt builder/parser API only "
                    + "accepts java.util.Date");

    // ---------------------------------------------------------------------------------------
    // C2 / C5 - domain scope: packages containing the "domain" segment, plus shared-kernel
    // in full.
    // ---------------------------------------------------------------------------------------

    private static DescribedPredicate<JavaClass> domainScope(String sharedKernelPackage) {
        return JavaClass.Predicates.resideInAPackage("..domain..")
                .or(JavaClass.Predicates.resideInAPackage(sharedKernelPackage + ".."));
    }

    static ArchRule noDoubleOrFloatFieldsInDomainScope(String sharedKernelPackage) {
        return noFields()
                .that().areDeclaredInClassesThat(domainScope(sharedKernelPackage))
                .should().haveRawType(double.class)
                .orShould().haveRawType(float.class)
                .because("money and other domain values must use precise types, never double or float");
    }

    static ArchRule noIllegalArgumentExceptionInDomainScope(String sharedKernelPackage) {
        return noClasses()
                .that(domainScope(sharedKernelPackage))
                .should().dependOnClassesThat().areAssignableTo(IllegalArgumentException.class)
                .because("domain code throws a specific DomainException with a stable code, never IllegalArgumentException");
    }

    // ---------------------------------------------------------------------------------------
    // C3 - a class with no defined responsibility, named after a bucket word, is forbidden.
    // ---------------------------------------------------------------------------------------

    static final ArchRule NO_FORBIDDEN_CLASS_NAME_SUFFIXES = noClasses()
            .should().haveSimpleNameEndingWith("Manager")
            .orShould().haveSimpleNameEndingWith("Helper")
            .orShould().haveSimpleNameEndingWith("Util")
            .orShould().haveSimpleNameEndingWith("Data")
            .orShould().haveSimpleNameEndingWith("Info")
            .because("a class named Manager/Helper/Util/Data/Info has no defined responsibility");

    // ---------------------------------------------------------------------------------------
    // C4 - a controller never depends directly on infrastructure or a repository; it goes
    // through a use case.
    // ---------------------------------------------------------------------------------------

    private static final DescribedPredicate<JavaClass> IS_CONTROLLER = DescribedPredicate.describe(
            "is annotated with @RestController or @Controller",
            javaClass -> javaClass.isAnnotatedWith(RestController.class) || javaClass.isAnnotatedWith(Controller.class));

    private static final DescribedPredicate<JavaClass> IS_INFRA_OR_REPOSITORY = JavaClass.Predicates
            .resideInAPackage("..infra..")
            .or(DescribedPredicate.describe("is a repository type", ArchitectureRules::isRepositoryType));

    private static boolean isRepositoryType(JavaClass javaClass) {
        return javaClass.getSimpleName().endsWith("Repository")
                || javaClass.isAssignableTo(Repository.class)
                || javaClass.isAssignableTo(CrudRepository.class)
                || javaClass.isAssignableTo(JpaRepository.class);
    }

    // identity.web.AuthController is a real @RestController now, so this rule runs a non-empty
    // check in production. allowEmptyShould stays as a safeguard for modules that don't have a
    // controller yet.
    static final ArchRule CONTROLLERS_MUST_NOT_DEPEND_ON_REPOSITORY_OR_INFRA = noClasses()
            .that(IS_CONTROLLER)
            .should().dependOnClassesThat(IS_INFRA_OR_REPOSITORY)
            .because("a controller must go through a use case, never touch infrastructure or a repository directly")
            .allowEmptyShould(true);

    // ---------------------------------------------------------------------------------------
    // D1 / D2 - app is the composition root only: no entities, no business logic.
    // ---------------------------------------------------------------------------------------

    static final ArchRule APP_MUST_NOT_CONTAIN_ENTITIES = noClasses()
            .that().resideInAPackage(APP_PACKAGE + "..")
            .should().beAnnotatedWith(Entity.class)
            .because("app is the composition root only, it must not hold JPA entities");

    static final ArchRule APP_MUST_NOT_CONTAIN_USE_CASES_OR_SERVICES = noClasses()
            .that().resideInAPackage(APP_PACKAGE + "..")
            .should().haveSimpleNameEndingWith("UseCase")
            .orShould().haveSimpleNameEndingWith("Service")
            .because("app is the composition root only, it must not hold business logic");
}
