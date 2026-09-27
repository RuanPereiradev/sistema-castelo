package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.NotFoundException;
import java.io.Serial;

/** There is no cash drawer session with the given id, or no open one. */
public class CashDrawerSessionNotFoundException extends NotFoundException {

    public static final String CODE = "CASH_DRAWER_SESSION_NOT_FOUND";

    @Serial
    private static final long serialVersionUID = 1L;

    public CashDrawerSessionNotFoundException(String detail) {
        super(CODE, detail);
    }
}
