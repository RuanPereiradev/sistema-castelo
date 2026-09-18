package br.com.castel.app.architecture.violations.modulegraph.fake.fakesharedkerneljakartapersistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * The one fixture in this tree that is <em>not</em> a violation: rule A4 must accept a shared-kernel
 * analog that uses the {@code jakarta.persistence} mapping annotations, which is what lets the audit
 * superclass be declared once for the whole project (decision #30 of task 0.5b).
 *
 * <p>A {@code @MappedSuperclass} and not an {@code @Entity}: an {@code @Entity} under
 * {@code br.com.castel} is swept by the entity scan of {@code CastelApplication} and would fail
 * {@code ddl-auto: validate} in every integration test, looking for a table that does not exist.
 */
@MappedSuperclass
public abstract class FakeSharedKernelUsingJakartaPersistence {

    @Column(name = "not_a_real_column")
    private String value;
}
