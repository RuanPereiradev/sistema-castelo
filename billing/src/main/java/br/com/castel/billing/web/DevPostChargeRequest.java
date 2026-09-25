package br.com.castel.billing.web;

import br.com.castel.billing.api.ChargeSourceType;
import jakarta.validation.constraints.NotNull;

/** Body of the {@code dev} route that posts a room night or a tab total on a folio. */
public class DevPostChargeRequest {

    @NotNull
    private ChargeSourceType sourceType;

    private String amount;

    private String description;

    public ChargeSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(ChargeSourceType sourceType) {
        this.sourceType = sourceType;
    }

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
}
