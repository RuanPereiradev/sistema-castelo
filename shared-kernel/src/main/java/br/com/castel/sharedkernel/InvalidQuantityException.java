package br.com.castel.sharedkernel;

import java.io.Serial;

/** Thrown when a {@link Quantity} falls outside the accepted range. */
public final class InvalidQuantityException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "INVALID_QUANTITY";

    InvalidQuantityException(String message) {
        super(CODE, message);
    }
}
