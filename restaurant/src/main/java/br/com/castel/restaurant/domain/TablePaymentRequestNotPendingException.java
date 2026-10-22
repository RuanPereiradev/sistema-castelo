package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** The payment request was already confirmed or rejected. */
public class TablePaymentRequestNotPendingException extends ConflictException {

    public static final String CODE = "TABLE_PAYMENT_REQUEST_NOT_PENDING";

    @Serial
    private static final long serialVersionUID = 1L;

    public TablePaymentRequestNotPendingException(String detail) {
        super(CODE, detail);
    }
}
