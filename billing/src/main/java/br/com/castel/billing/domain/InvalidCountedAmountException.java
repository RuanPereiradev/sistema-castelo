package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The amount counted at closing is missing or below zero. */
public class InvalidCountedAmountException extends DomainException {

    public static final String CODE = "INVALID_COUNTED_AMOUNT";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCountedAmountException(String detail) {
        super(CODE, detail);
    }
}
