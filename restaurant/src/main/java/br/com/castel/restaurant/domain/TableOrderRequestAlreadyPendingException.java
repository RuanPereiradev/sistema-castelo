package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/**
 * The device already has a {@code PENDING} request on the dining table: one waiting per device, no
 * longer per table (decision #62 of task qr-table-payment), held by
 * {@code idx_table_order_request_pending_by_device}.
 */
public class TableOrderRequestAlreadyPendingException extends ConflictException {

    public static final String CODE = "TABLE_ORDER_REQUEST_ALREADY_PENDING";

    @Serial
    private static final long serialVersionUID = 1L;

    public TableOrderRequestAlreadyPendingException(String detail) {
        super(CODE, detail);
    }
}
