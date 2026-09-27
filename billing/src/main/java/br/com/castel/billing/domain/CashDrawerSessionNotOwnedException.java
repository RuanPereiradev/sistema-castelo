package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** Only whoever opened the cash drawer session, or an {@code ADMIN}, closes it (decision C5 of task 2.4). */
public class CashDrawerSessionNotOwnedException extends ConflictException {

    public static final String CODE = "CASH_DRAWER_SESSION_NOT_OWNED";

    @Serial
    private static final long serialVersionUID = 1L;

    public CashDrawerSessionNotOwnedException(String detail) {
        super(CODE, detail);
    }
}
