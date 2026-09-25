package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The idempotency key of a payment is missing, blank or longer than 100 characters. */
public class InvalidIdempotencyKeyException extends DomainException {

    public static final String CODE = "INVALID_IDEMPOTENCY_KEY";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidIdempotencyKeyException(String detail) {
        super(CODE, detail);
    }
}
