package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** The request was already confirmed, rejected or expired (decision #23). */
public class TableOrderRequestNotPendingException extends ConflictException {

    public static final String CODE = "TABLE_ORDER_REQUEST_NOT_PENDING";

    @Serial
    private static final long serialVersionUID = 1L;

    public TableOrderRequestNotPendingException(String detail) {
        super(CODE, detail);
    }
}
