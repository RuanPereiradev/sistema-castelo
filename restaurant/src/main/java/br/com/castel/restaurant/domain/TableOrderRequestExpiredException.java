package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** The request is still {@code PENDING} but its 30 minutes are over (decision #14). */
public class TableOrderRequestExpiredException extends ConflictException {

    public static final String CODE = "TABLE_ORDER_REQUEST_EXPIRED";

    @Serial
    private static final long serialVersionUID = 1L;

    public TableOrderRequestExpiredException(String detail) {
        super(CODE, detail);
    }
}
