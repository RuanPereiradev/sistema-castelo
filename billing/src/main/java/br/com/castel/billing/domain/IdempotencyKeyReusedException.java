package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/**
 * The idempotency key was already used by a payment of another folio, method or amount (decision #5
 * of task 1.3), or by a cash movement of another session, type or amount (decision C9 of task 2.4).
 */
public class IdempotencyKeyReusedException extends ConflictException {

    public static final String CODE = "IDEMPOTENCY_KEY_REUSED";

    @Serial
    private static final long serialVersionUID = 1L;

    public IdempotencyKeyReusedException(String detail) {
        super(CODE, detail);
    }
}
