package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** Reopening a closing tab needs a reason of 1 to 500 characters once trimmed. */
public class InvalidReopeningReasonException extends DomainException {

    public static final String CODE = "INVALID_REOPENING_REASON";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidReopeningReasonException(String detail) {
        super(CODE, detail);
    }
}
