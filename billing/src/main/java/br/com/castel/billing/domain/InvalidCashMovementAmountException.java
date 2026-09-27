package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** A cash drop or supply is missing its amount, or the amount is not greater than zero. */
public class InvalidCashMovementAmountException extends DomainException {

    public static final String CODE = "INVALID_CASH_MOVEMENT_AMOUNT";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCashMovementAmountException(String detail) {
        super(CODE, detail);
    }
}
