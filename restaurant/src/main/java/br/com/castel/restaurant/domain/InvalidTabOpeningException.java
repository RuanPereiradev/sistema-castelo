package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The origin of the tab came without the field it needs, or with the field of the other origin. */
public class InvalidTabOpeningException extends DomainException {

    public static final String CODE = "INVALID_TAB_OPENING";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidTabOpeningException(String detail) {
        super(CODE, detail);
    }
}
