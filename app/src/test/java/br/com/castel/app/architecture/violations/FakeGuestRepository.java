package br.com.castel.app.architecture.violations;

/**
 * Fixture for C4: a repository interface, the kind of type a controller must never depend on
 * directly.
 *
 * <p>Deliberately does not extend a real Spring Data interface: the rule already catches any
 * type simply named {@code ...Repository}, and extending {@code JpaRepository} here would make
 * Spring Data's repository scan (which reaches this package too) try to build a real proxy
 * backed by a non-entity type, breaking unrelated {@code @SpringBootTest} contexts.
 */
public interface FakeGuestRepository {
}
