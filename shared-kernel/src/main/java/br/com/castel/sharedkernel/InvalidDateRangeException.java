package br.com.castel.sharedkernel;

import java.io.Serial;

/**
 * Thrown when a {@link DateRange} has a missing bound, its end is not after its start or it spans more
 * than 365 nights. The message never includes the rejected dates.
 */
public final class InvalidDateRangeException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "INVALID_DATE_RANGE";

    InvalidDateRangeException(String message) {
        super(CODE, message);
    }
}
