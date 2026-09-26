package br.com.castel.billing.web;

/**
 * Body of {@code POST /api/billing/folios/{folioId}/adjustments}. The amount is a decimal string,
 * negative for a discount. No bean validation: every field is refused by the domain with its code.
 */
public class AdjustmentRequest {

    private String amount;

    private String description;

    private String reason;

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
