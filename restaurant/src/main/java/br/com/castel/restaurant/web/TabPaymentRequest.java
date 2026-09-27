package br.com.castel.restaurant.web;

import br.com.castel.billing.api.PaymentMethod;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/restaurant/tabs/{tabId}/payments}; the idempotency key travels in the
 * {@code Idempotency-Key} header, as on the route of the folio.
 *
 * <p>The amount is a decimal string, and whatever billing refuses with a code — zero, negative,
 * malformed, above the balance — is left to it. Only a missing method is refused here, as billing
 * does on its own route; a method outside the enum is a 400 of the global handler.
 */
public class TabPaymentRequest {

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
