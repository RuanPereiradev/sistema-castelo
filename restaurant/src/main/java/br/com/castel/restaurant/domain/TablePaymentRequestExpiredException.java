package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** The payment request is past its 30 minutes, whether or not the job already stored it as expired. */
public class TablePaymentRequestExpiredException extends ConflictException {

    public static final String CODE = "TABLE_PAYMENT_REQUEST_EXPIRED";

    @Serial
    private static final long serialVersionUID = 1L;

    public TablePaymentRequestExpiredException(String detail) {
        super(CODE, detail);
    }
}
