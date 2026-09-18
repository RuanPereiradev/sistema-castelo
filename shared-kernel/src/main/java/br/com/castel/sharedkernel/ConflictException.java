package br.com.castel.sharedkernel;

import java.io.Serial;

/**
 * A domain exception meaning "the aggregate is not in a state that accepts this".
 *
 * <p>Pure HTTP translation marker: the global exception handler answers 409 for every subclass.
 * Use it when the request is valid and the resource exists, but the current state rejects the
 * operation (closing a tab that is already closed, booking a date already sold out). A rule broken
 * by the <em>content</em> of the request stays a plain {@link DomainException}, which answers 422.
 *
 * <p>It carries no behaviour of its own, and subclasses keep passing an explicit, stable
 * {@code code}.
 */
public abstract class ConflictException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    protected ConflictException(String code, String message) {
        super(code, message);
    }
}
