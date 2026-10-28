package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** The item is in a payment request waiting for the waiter (decisions #8 and #21 of task qr-table-payment). */
public class TabItemReservedForPaymentException extends ConflictException {

    public static final String CODE = "TAB_ITEM_RESERVED_FOR_PAYMENT";

    @Serial
    private static final long serialVersionUID = 1L;

    public TabItemReservedForPaymentException(String detail) {
        super(CODE, detail);
    }
}
