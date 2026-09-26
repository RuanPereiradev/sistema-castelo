package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The special instructions go past 200 characters once trimmed. */
public class InvalidSpecialInstructionsException extends DomainException {

    public static final String CODE = "INVALID_SPECIAL_INSTRUCTIONS";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidSpecialInstructionsException(String detail) {
        super(CODE, detail);
    }
}
