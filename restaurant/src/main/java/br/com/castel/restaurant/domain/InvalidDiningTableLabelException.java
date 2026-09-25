package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The label of a dining table is missing, blank or longer than what the column holds. */
public class InvalidDiningTableLabelException extends DomainException {

    public static final String CODE = "INVALID_DINING_TABLE_LABEL";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidDiningTableLabelException(String detail) {
        super(CODE, detail);
    }
}
