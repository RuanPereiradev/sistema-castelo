package br.com.castel.billing.application;

import br.com.castel.billing.domain.Payment;
import br.com.castel.sharedkernel.Money;
import java.util.Objects;

/**
 * A payment registered, or recognised as a retry, and the balance of its folio right after it.
 * The balance is read again on a retry, so it reflects whatever happened since the first attempt.
 */
public record ReceivedPayment(Payment payment, Money balance) {

    public ReceivedPayment {
        Objects.requireNonNull(payment, "payment");
        Objects.requireNonNull(balance, "balance");
    }
}
