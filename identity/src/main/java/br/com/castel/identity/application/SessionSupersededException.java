package br.com.castel.identity.application;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/**
 * Thrown when a token's {@code tokenVersion} claim no longer matches the version stored on the
 * user: a newer login or a logout revoked it in the meantime.
 */
public final class SessionSupersededException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "SESSION_SUPERSEDED";

    public SessionSupersededException() {
        super(CODE, "Session superseded by a more recent login or logout");
    }
}
