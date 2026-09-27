package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The reason of a cash drop or supply is blank or longer than 500 characters. */
public class InvalidCashMovementReasonException extends DomainException {

    public static final String CODE = "INVALID_CASH_MOVEMENT_REASON";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCashMovementReasonException(String detail) {
        super(CODE, detail);
    }
}
