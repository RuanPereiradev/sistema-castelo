package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** The cash drawer session is closed and takes no further movement nor a second closing. */
public class CashDrawerSessionClosedException extends ConflictException {

    public static final String CODE = "CASH_DRAWER_SESSION_CLOSED";

    @Serial
    private static final long serialVersionUID = 1L;

    public CashDrawerSessionClosedException(String detail) {
        super(CODE, detail);
    }
}
