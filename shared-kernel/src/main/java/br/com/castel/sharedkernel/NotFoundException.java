package br.com.castel.sharedkernel;

import java.io.Serial;

/**
 * A domain exception meaning "the resource you asked for does not exist".
 *
 * <p>Pure HTTP translation marker: the global exception handler answers 404 for every subclass,
 * so no module has to enumerate its own not-found exceptions in a web layer. It carries no
 * behaviour of its own, and subclasses keep passing an explicit, stable {@code code}.
 */
public abstract class NotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    protected NotFoundException(String code, String message) {
        super(code, message);
    }
}
