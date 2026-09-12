package br.com.castel.app.architecture.violations.modulegraph.fake.fakesharedkernel;

import org.springframework.stereotype.Component;

/**
 * Fixture for A4: a shared-kernel analog must stay a plain Java module, free of Spring.
 *
 * <p>Declared {@code abstract} so that Spring's component scan (which reaches this package too)
 * never instantiates it as a real bean; the {@code @Component} annotation is still present in
 * the bytecode, which is all ArchUnit inspects.
 */
@Component
public abstract class FakeSharedKernelUsingSpring {
}
