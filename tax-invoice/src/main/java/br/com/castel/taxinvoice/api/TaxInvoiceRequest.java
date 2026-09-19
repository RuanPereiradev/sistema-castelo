package br.com.castel.taxinvoice.api;

import br.com.castel.sharedkernel.Cpf;
import br.com.castel.sharedkernel.Money;
import java.util.Objects;
import java.util.Optional;

/**
 * What the system asks to be invoiced.
 *
 * <p>The minimum to declare the port. The fields a real issuer needs — service or product codes, tax
 * regime, the breakdown per item — depend on how the property issues today, which the technical plan
 * keeps as an open pendency for v1.2.
 *
 * <p>{@code payerDocument} is an {@link Optional} component on purpose: whether the payer asked for
 * the document in their name is part of the contract, not something a caller should learn from a
 * null.
 */
public record TaxInvoiceRequest(
        Money amount, String description, Optional<Cpf> payerDocument, String reference) {

    public TaxInvoiceRequest {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(payerDocument, "payerDocument");
        Objects.requireNonNull(reference, "reference");
    }

    /** For the common case: the payer did not ask for the document in their name. */
    public static TaxInvoiceRequest withoutPayerDocument(Money amount, String description, String reference) {
        return new TaxInvoiceRequest(amount, description, Optional.empty(), reference);
    }
}
