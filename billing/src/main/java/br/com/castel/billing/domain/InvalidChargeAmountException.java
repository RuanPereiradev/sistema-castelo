package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** A posted charge must be greater than zero, and an adjustment different from zero. */
public class InvalidChargeAmountException extends DomainException {

    public static final String CODE = "INVALID_CHARGE_AMOUNT";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidChargeAmountException(String detail) {
        super(CODE, detail);
    }
}
