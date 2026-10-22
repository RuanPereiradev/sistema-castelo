package br.com.castel.restaurant.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Body of {@code POST /public/tables/{orderingToken}/payment-requests/quote}: the units and whether the
 * service charge goes on them, as in {@link SubmitTablePaymentRequest}, without the customer — the
 * simulation writes nothing (decision #56 of task qr-table-payment). {@code includeServiceCharge}
 * and each {@code tabItemId} are required (400).
 */
public class QuoteTablePaymentRequest {

    @NotNull
    private Boolean includeServiceCharge;

    @Valid
    private List<SubmitTablePaymentRequest.Item> items;

    public Boolean getIncludeServiceCharge() {
        return includeServiceCharge;
    }

    public void setIncludeServiceCharge(Boolean includeServiceCharge) {
        this.includeServiceCharge = includeServiceCharge;
    }

    public List<SubmitTablePaymentRequest.Item> getItems() {
        return items;
    }

    public void setItems(List<SubmitTablePaymentRequest.Item> items) {
        this.items = items;
    }
}
