package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The price of a modifier is missing or negative. Zero is valid: a choice at no cost. */
public class InvalidModifierPriceException extends DomainException {

    public static final String CODE = "INVALID_MODIFIER_PRICE";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidModifierPriceException(String detail) {
        super(CODE, detail);
    }
}
