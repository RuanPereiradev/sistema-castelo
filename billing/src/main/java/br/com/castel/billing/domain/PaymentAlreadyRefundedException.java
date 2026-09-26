package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** Only a confirmed payment can be refunded (decision #2 of task 1.3). */
public class PaymentAlreadyRefundedException extends ConflictException {

    public static final String CODE = "PAYMENT_ALREADY_REFUNDED";

    @Serial
    private static final long serialVersionUID = 1L;

    public PaymentAlreadyRefundedException(String detail) {
        super(CODE, detail);
    }
}
