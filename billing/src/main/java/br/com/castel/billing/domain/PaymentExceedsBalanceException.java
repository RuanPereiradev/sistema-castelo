package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** A tab folio takes no payment above what it owes (decision #1 of task 1.3). */
public class PaymentExceedsBalanceException extends DomainException {

    public static final String CODE = "PAYMENT_EXCEEDS_BALANCE";

    @Serial
    private static final long serialVersionUID = 1L;

    public PaymentExceedsBalanceException(String detail) {
        super(CODE, detail);
    }
}
