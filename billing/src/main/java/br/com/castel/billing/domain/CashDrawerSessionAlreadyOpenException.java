package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** The property already has an open cash drawer session (decision C1 of task 2.4). */
public class CashDrawerSessionAlreadyOpenException extends ConflictException {

    public static final String CODE = "CASH_DRAWER_SESSION_ALREADY_OPEN";

    @Serial
    private static final long serialVersionUID = 1L;

    public CashDrawerSessionAlreadyOpenException(String detail) {
        super(CODE, detail);
    }
}
