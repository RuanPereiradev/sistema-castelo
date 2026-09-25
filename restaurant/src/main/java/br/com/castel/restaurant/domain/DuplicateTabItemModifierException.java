package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The same modifier came twice in one order. */
public class DuplicateTabItemModifierException extends DomainException {

    public static final String CODE = "DUPLICATE_TAB_ITEM_MODIFIER";

    @Serial
    private static final long serialVersionUID = 1L;

    public DuplicateTabItemModifierException(String detail) {
        super(CODE, detail);
    }
}
