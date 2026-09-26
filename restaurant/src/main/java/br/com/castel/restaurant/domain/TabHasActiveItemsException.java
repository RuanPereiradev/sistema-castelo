package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** A tab is cancelled only when every item on it is cancelled (decision #10). */
public class TabHasActiveItemsException extends ConflictException {

    public static final String CODE = "TAB_HAS_ACTIVE_ITEMS";

    @Serial
    private static final long serialVersionUID = 1L;

    public TabHasActiveItemsException(String detail) {
        super(CODE, detail);
    }
}
