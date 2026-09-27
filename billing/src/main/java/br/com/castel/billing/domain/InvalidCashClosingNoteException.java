package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/**
 * The closing note is longer than 500 characters. The note itself is optional, with a difference or
 * without one (decision #22 of task 2.4).
 */
public class InvalidCashClosingNoteException extends DomainException {

    public static final String CODE = "INVALID_CASH_CLOSING_NOTE";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCashClosingNoteException(String detail) {
        super(CODE, detail);
    }
}
