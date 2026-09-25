package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The method is not accepted on a manually registered payment (decision #6 of task 1.3). */
public class PaymentMethodNotAcceptedException extends DomainException {

    public static final String CODE = "PAYMENT_METHOD_NOT_ACCEPTED";

    @Serial
    private static final long serialVersionUID = 1L;

    public PaymentMethodNotAcceptedException(String detail) {
        super(CODE, detail);
    }
}
