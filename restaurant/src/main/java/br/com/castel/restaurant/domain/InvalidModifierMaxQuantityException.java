package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** How many of a modifier an item accepts must lie between 1 and 99. */
public class InvalidModifierMaxQuantityException extends DomainException {

    public static final String CODE = "INVALID_MODIFIER_MAX_QUANTITY";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidModifierMaxQuantityException(String detail) {
        super(CODE, detail);
    }
}
