package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The number of seats of a dining table was given and falls outside the accepted range. */
public class InvalidDiningTableSeatsException extends DomainException {

    public static final String CODE = "INVALID_DINING_TABLE_SEATS";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidDiningTableSeatsException(String detail) {
        super(CODE, detail);
    }
}
