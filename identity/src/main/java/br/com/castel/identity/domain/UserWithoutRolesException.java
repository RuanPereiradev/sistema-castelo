package br.com.castel.identity.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** Thrown when a {@link User} would be created without any role. */
public final class UserWithoutRolesException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "USER_WITHOUT_ROLES";

    public UserWithoutRolesException() {
        super(CODE, "User must have at least one role");
    }
}
