package br.com.castel.identity.application;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/**
 * Thrown when login fails, for any reason: user not found, wrong password, or inactive user.
 *
 * <p>Deliberately a single exception for all three cases, with a single generic message. A
 * distinct message per case would let an attacker enumerate which usernames exist.
 */
public final class InvalidCredentialsException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "INVALID_CREDENTIALS";

    public InvalidCredentialsException() {
        super(CODE, "Invalid username or password");
    }
}
