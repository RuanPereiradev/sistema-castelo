package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The area of a dining table is longer than what the column holds. */
public class InvalidDiningTableAreaException extends DomainException {

    public static final String CODE = "INVALID_DINING_TABLE_AREA";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidDiningTableAreaException(String detail) {
        super(CODE, detail);
    }
}
