package br.com.castel.identity.application;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/**
 * Thrown when a JWT is malformed, has an invalid signature, or its {@code type} claim does not
 * match the type expected by the caller (a refresh token used as an access token, or vice versa).
 */
public final class InvalidTokenException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "INVALID_TOKEN";

    public InvalidTokenException() {
        super(CODE, "Invalid token");
    }
}
