package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** A cancellation needs a reason of 1 to 500 characters once trimmed. */
public class InvalidCancellationReasonException extends DomainException {

    public static final String CODE = "INVALID_CANCELLATION_REASON";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCancellationReasonException(String detail) {
        super(CODE, detail);
    }
}
