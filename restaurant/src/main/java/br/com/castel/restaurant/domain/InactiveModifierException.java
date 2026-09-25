package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** A deactivated modifier cannot be offered on an item. */
public class InactiveModifierException extends DomainException {

    public static final String CODE = "INACTIVE_MODIFIER";

    @Serial
    private static final long serialVersionUID = 1L;

    public InactiveModifierException(String detail) {
        super(CODE, detail);
    }
}
