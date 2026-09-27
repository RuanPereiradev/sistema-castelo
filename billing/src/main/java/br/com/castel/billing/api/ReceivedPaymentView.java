package br.com.castel.billing.api;

import br.com.castel.sharedkernel.Money;
import java.time.Instant;
import java.util.Objects;

/**
 * A payment as another module reads it, right after registering it, and the balance of its folio at
 * that moment. A retry with the same idempotency key answers the original payment, with the balance
 * read again.
 */
public record ReceivedPaymentView(
        PaymentId paymentId, PaymentMethod method, Money amount, Instant paidAt, Money balance) {

    public ReceivedPaymentView {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(paidAt, "paidAt");
        Objects.requireNonNull(balance, "balance");
    }
}
