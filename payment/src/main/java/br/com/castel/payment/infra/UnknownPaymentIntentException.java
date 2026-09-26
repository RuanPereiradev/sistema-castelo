package br.com.castel.payment.infra;

import br.com.castel.payment.api.PaymentIntentId;
import br.com.castel.sharedkernel.NotFoundException;
import java.io.Serial;

/** The fake acquirer was asked about an intent it never created. */
public class UnknownPaymentIntentException extends NotFoundException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "PAYMENT_INTENT_NOT_FOUND";

    public UnknownPaymentIntentException(PaymentIntentId intentId) {
        super(CODE, "Payment intent not found: " + intentId.value());
    }
}
