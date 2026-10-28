package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** Only a confirmed payment request not refunded yet is refunded (decision #30 of task qr-table-payment). */
public class TablePaymentRequestNotRefundableException extends ConflictException {

    public static final String CODE = "TABLE_PAYMENT_REQUEST_NOT_REFUNDABLE";

    @Serial
    private static final long serialVersionUID = 1L;

    public TablePaymentRequestNotRefundableException(String detail) {
        super(CODE, detail);
    }
}
