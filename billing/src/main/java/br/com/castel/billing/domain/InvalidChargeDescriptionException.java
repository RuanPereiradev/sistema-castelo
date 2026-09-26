package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The description of a charge is missing, blank or longer than the column holds. */
public class InvalidChargeDescriptionException extends DomainException {

    public static final String CODE = "INVALID_CHARGE_DESCRIPTION";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidChargeDescriptionException(String detail) {
        super(CODE, detail);
    }
}
