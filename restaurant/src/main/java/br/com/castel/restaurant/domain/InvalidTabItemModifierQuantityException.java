package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The quantity of a modifier falls outside 1 to the maximum the item accepts per unit. */
public class InvalidTabItemModifierQuantityException extends DomainException {

    public static final String CODE = "INVALID_TAB_ITEM_MODIFIER_QUANTITY";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidTabItemModifierQuantityException(String detail) {
        super(CODE, detail);
    }
}
