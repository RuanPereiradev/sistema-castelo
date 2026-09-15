package br.com.castel.identity.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/**
 * Thrown when a {@link User} username does not follow the format required for login: 3 to 30
 * characters, lowercase letters, digits, dot and underscore only.
 *
 * <p>The message never includes the rejected value.
 */
public final class InvalidUsernameException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "INVALID_USERNAME";

    public InvalidUsernameException(String message) {
        super(CODE, message);
    }
}
