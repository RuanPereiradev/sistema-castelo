package br.com.castel.identity.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/**
 * Thrown when a {@link User} password has fewer than {@link User#MINIMUM_PASSWORD_LENGTH} Unicode
 * code points, on creation or on change.
 *
 * <p>The message never includes the rejected value.
 */
public final class WeakPasswordException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "WEAK_PASSWORD";

    public WeakPasswordException() {
        super(CODE, "Password must have at least " + User.MINIMUM_PASSWORD_LENGTH + " characters");
    }
}
