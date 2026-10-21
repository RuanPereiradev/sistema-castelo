package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** The tab of the dining table is {@code CLOSING}: the bill is being settled and the phone takes no order (decision #9). */
public class TabClosingRefusesOrderRequestException extends ConflictException {

    public static final String CODE = "TAB_CLOSING_REFUSES_ORDER_REQUEST";

    @Serial
    private static final long serialVersionUID = 1L;

    public TabClosingRefusesOrderRequestException(String detail) {
        super(CODE, detail);
    }
}
