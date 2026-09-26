package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** A self-service card number falls outside 1 to 999 (decision #2). */
public class InvalidCardNumberException extends DomainException {

    public static final String CODE = "INVALID_CARD_NUMBER";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCardNumberException(String detail) {
        super(CODE, detail);
    }
}
