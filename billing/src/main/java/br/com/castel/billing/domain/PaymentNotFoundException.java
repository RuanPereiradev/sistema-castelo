package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.NotFoundException;
import java.io.Serial;

/** The folio has no payment with the given id. */
public class PaymentNotFoundException extends NotFoundException {

    public static final String CODE = "PAYMENT_NOT_FOUND";

    @Serial
    private static final long serialVersionUID = 1L;

    public PaymentNotFoundException(String detail) {
        super(CODE, detail);
    }
}
