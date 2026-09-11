package br.com.castel.sharedkernel;

import java.io.Serial;

/** Thrown when an {@link EntityId} value is null or its text is not a canonical UUID. */
public final class InvalidEntityIdException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "INVALID_ENTITY_ID";

    InvalidEntityIdException(String message) {
        super(CODE, message);
    }
}
