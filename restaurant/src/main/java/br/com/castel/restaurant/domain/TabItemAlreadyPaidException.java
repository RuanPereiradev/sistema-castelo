package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** The item was paid from the phone of the table: it is not chosen again, cancelled nor moved until the payment is refunded (decisions #9 and #21 of task qr-table-payment). */
public class TabItemAlreadyPaidException extends ConflictException {

    public static final String CODE = "TAB_ITEM_ALREADY_PAID";

    @Serial
    private static final long serialVersionUID = 1L;

    public TabItemAlreadyPaidException(String detail) {
        super(CODE, detail);
    }
}
