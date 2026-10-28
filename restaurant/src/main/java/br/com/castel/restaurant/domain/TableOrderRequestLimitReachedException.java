package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** The dining table already has 10 requests waiting for the waiter (decision #62 of task qr-table-payment). */
public class TableOrderRequestLimitReachedException extends ConflictException {

    public static final String CODE = "TABLE_ORDER_REQUEST_LIMIT_REACHED";

    @Serial
    private static final long serialVersionUID = 1L;

    public TableOrderRequestLimitReachedException(String detail) {
        super(CODE, detail);
    }
}
