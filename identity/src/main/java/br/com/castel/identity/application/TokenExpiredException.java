package br.com.castel.identity.application;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** Thrown when a JWT is well-formed and correctly signed, but past its expiration. */
public final class TokenExpiredException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "TOKEN_EXPIRED";

    public TokenExpiredException() {
        super(CODE, "Token expired");
    }
}
