package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The name of a modifier is blank or longer than what the column holds. */
public class InvalidModifierNameException extends DomainException {

    public static final String CODE = "INVALID_MODIFIER_NAME";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidModifierNameException(String detail) {
        super(CODE, detail);
    }
}
