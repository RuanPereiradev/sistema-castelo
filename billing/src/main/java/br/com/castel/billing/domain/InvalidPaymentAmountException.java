package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** A payment must be greater than zero. */
public class InvalidPaymentAmountException extends DomainException {

    public static final String CODE = "INVALID_PAYMENT_AMOUNT";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidPaymentAmountException(String detail) {
        super(CODE, detail);
    }
}
