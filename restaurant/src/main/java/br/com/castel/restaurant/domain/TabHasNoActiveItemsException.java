package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** A tab with no active item has nothing to charge, so its closing does not start. */
public class TabHasNoActiveItemsException extends ConflictException {

    public static final String CODE = "TAB_HAS_NO_ACTIVE_ITEMS";

    @Serial
    private static final long serialVersionUID = 1L;

    public TabHasNoActiveItemsException(String detail) {
        super(CODE, detail);
    }
}
