package br.com.castel.payment.api;

import br.com.castel.sharedkernel.Money;
import java.util.Objects;

/**
 * What the system asks an acquirer to charge.
 *
 * <p>Named for what it is, rather than {@code ChargeRequest} as the technical plan first wrote it:
 * a charge at an acquirer and a posting on a folio are different things, and the glossary keeps
 * {@code Charge} for the posting (decision #4).
 *
 * <p>{@code reference} is whatever the caller wants to recognise this payment by later — a folio
 * code, a tab number. It travels to the acquirer and comes back in the statement.
 */
public record PaymentRequest(Money amount, String description, String reference) {

    public PaymentRequest {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(reference, "reference");
    }
}
