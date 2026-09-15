package br.com.castel.identity.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/**
 * Thrown when a {@link User} password is longer than {@link User#MAXIMUM_PASSWORD_BYTES} bytes in
 * UTF-8, on creation or on change. BCrypt only reads the first 72 bytes, so a longer password
 * would be silently truncated.
 *
 * <p>The message never includes the rejected value.
 */
public final class PasswordTooLongException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "PASSWORD_TOO_LONG";

    public PasswordTooLongException() {
        super(CODE, "Password must have at most " + User.MAXIMUM_PASSWORD_BYTES + " bytes in UTF-8");
    }
}
