package br.com.castel.sharedkernel;

import java.io.Serial;

/** Thrown when a {@link Percentage} is null, unparseable, negative or finer than the stored precision. */
public final class InvalidPercentageException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "INVALID_PERCENTAGE";

    InvalidPercentageException(String message) {
        super(CODE, message);
    }
}
