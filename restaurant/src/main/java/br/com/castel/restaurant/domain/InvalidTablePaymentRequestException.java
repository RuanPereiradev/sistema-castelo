package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The payment request came without items, or with an item cancelled or not on the tab of the table (decision #36 of task qr-table-payment). */
public class InvalidTablePaymentRequestException extends DomainException {

    public static final String CODE = "INVALID_TABLE_PAYMENT_REQUEST";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidTablePaymentRequestException(String detail) {
        super(CODE, detail);
    }
}
