package br.com.castel.billing.web;

import br.com.castel.billing.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/billing/folios/{folioId}/payments}. The idempotency key travels in the
 * {@code Idempotency-Key} header.
 *
 * <p>The amount is a decimal string, and whatever the domain refuses with a code — zero, negative,
 * malformed — is left to it. Only a missing method is refused here: the domain has no code for it,
 * and a method outside the enum is already a 400 of the global handler.
 */
public class PaymentRequest {

    @NotNull
    private PaymentMethod method;

    private String amount;

    public PaymentMethod getMethod() {
        return method;
    }

    public void setMethod(PaymentMethod method) {
        this.method = method;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }
}
