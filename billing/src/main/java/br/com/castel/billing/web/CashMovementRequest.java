package br.com.castel.billing.web;

/**
 * Body of a cash drop or a cash supply. The idempotency key travels in the {@code Idempotency-Key}
 * header. Whatever the domain refuses with a code — zero, negative, a blank reason — is left to it.
 */
public class CashMovementRequest {

    private String amount;

    private String reason;

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
