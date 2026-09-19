package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** A window with equal start and end times says nothing about when the item is served. */
public class InvalidAvailabilityWindowException extends DomainException {

    public static final String CODE = "INVALID_AVAILABILITY_WINDOW";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidAvailabilityWindowException(String detail) {
        super(CODE, detail);
    }
}
