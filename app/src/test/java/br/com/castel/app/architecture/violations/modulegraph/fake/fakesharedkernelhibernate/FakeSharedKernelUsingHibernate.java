package br.com.castel.app.architecture.violations.modulegraph.fake.fakesharedkernelhibernate;

import org.hibernate.annotations.Immutable;

/**
 * Fixture for A4: a shared-kernel analog may carry the JPA mapping annotations, but never the
 * persistence provider's own. {@code @Immutable} is Hibernate's, not {@code jakarta.persistence}'s.
 *
 * <p>Alone in its package so the proof shows the Hibernate clause of the rule working, and not the
 * Spring one firing again. Declared {@code abstract} so the component scan that reaches this package
 * never instantiates it; the annotation stays in the bytecode, which is all ArchUnit reads.
 */
@Immutable
public abstract class FakeSharedKernelUsingHibernate {
}
