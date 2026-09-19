package br.com.castel.payment.api;

import br.com.castel.sharedkernel.Money;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * An attempt to charge someone, as the acquirer answered it.
 *
 * <p>{@code payerUrl} is where the payer completes it — a checkout page, a PIX payload. Empty when
 * the acquirer settles without sending the payer anywhere.
 */
public record PaymentIntent(
        PaymentIntentId intentId, PaymentStatus status, Money amount, Optional<URI> payerUrl) {

    public PaymentIntent {
        Objects.requireNonNull(intentId, "intentId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(payerUrl, "payerUrl");
    }
}
