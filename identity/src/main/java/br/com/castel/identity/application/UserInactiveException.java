package br.com.castel.identity.application;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/**
 * Thrown when a well-formed, still current token belongs to a user who can no longer authenticate
 * because the user was deactivated.
 *
 * <p>Only raised for tokens (access and refresh). A login attempt of an inactive user is still
 * rejected with {@link InvalidCredentialsException}, identical to an unknown user or a wrong
 * password.
 */
public final class UserInactiveException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "USER_INACTIVE";

    public UserInactiveException() {
        super(CODE, "User is inactive");
    }
}
