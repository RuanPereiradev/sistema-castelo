package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** A cash payment needs an open cash drawer session while cash control is on (decision C2 of task 2.4). */
public class CashDrawerSessionNotOpenException extends ConflictException {

    public static final String CODE = "CASH_DRAWER_SESSION_NOT_OPEN";

    @Serial
    private static final long serialVersionUID = 1L;

    public CashDrawerSessionNotOpenException(String detail) {
        super(CODE, detail);
    }
}
