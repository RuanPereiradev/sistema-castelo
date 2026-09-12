package br.com.castel.app.architecture.violations;

import org.springframework.web.bind.annotation.RestController;

/**
 * Fixture for C4: a controller must go through a use case, never touch a repository directly.
 *
 * <p>Declared {@code abstract} so that Spring's component scan (which reaches this package too)
 * never instantiates it as a real bean; the {@code @RestController} annotation and the field
 * dependency are still present in the bytecode, which is all ArchUnit inspects.
 */
@RestController
public abstract class RestControllerDependingOnRepository {

    private final FakeGuestRepository fakeGuestRepository;

    protected RestControllerDependingOnRepository(FakeGuestRepository fakeGuestRepository) {
        this.fakeGuestRepository = fakeGuestRepository;
    }
}
