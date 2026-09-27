package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The number of guests of a tab goes from 1 to 999. */
public class InvalidGuestCountException extends DomainException {

    public static final String CODE = "INVALID_GUEST_COUNT";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidGuestCountException(String detail) {
        super(CODE, detail);
    }
}
