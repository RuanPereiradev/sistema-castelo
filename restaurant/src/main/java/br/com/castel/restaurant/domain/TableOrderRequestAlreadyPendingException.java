package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** The dining table already has a {@code PENDING} request (decision #15), held by {@code idx_table_order_request_pending_by_table}. */
public class TableOrderRequestAlreadyPendingException extends ConflictException {

    public static final String CODE = "TABLE_ORDER_REQUEST_ALREADY_PENDING";

    @Serial
    private static final long serialVersionUID = 1L;

    public TableOrderRequestAlreadyPendingException(String detail) {
        super(CODE, detail);
    }
}
