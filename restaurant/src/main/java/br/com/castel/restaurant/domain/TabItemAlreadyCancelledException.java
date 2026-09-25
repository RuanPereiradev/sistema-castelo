package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** The item was already cancelled; its author and reason are kept as they were. */
public class TabItemAlreadyCancelledException extends ConflictException {

    public static final String CODE = "TAB_ITEM_ALREADY_CANCELLED";

    @Serial
    private static final long serialVersionUID = 1L;

    public TabItemAlreadyCancelledException(String detail) {
        super(CODE, detail);
    }
}
