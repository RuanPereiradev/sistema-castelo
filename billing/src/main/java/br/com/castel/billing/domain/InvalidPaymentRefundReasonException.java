package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The reason of a payment refund is missing, blank or too long. */
public class InvalidPaymentRefundReasonException extends DomainException {

    public static final String CODE = "INVALID_PAYMENT_REFUND_REASON";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidPaymentRefundReasonException(String detail) {
        super(CODE, detail);
    }
}
