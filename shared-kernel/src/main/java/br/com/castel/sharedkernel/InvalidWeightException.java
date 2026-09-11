package br.com.castel.sharedkernel;

import java.io.Serial;

/** Thrown when a {@link Weight} is null, not positive or not a whole number of grams. */
public final class InvalidWeightException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "INVALID_WEIGHT";

    InvalidWeightException(String message) {
        super(CODE, message);
    }
}
