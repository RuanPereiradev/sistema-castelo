package br.com.castel.restaurant.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Body of {@code POST /public/tables/{orderingToken}/payment-requests}: who pays (decision #40 of task
 * qr-table-payment), the units of each item they pay (#39) and whether they pay the service charge
 * on them (#38).
 *
 * <p>Only what has nothing to load from is checked here: {@code includeServiceCharge} and each
 * {@code tabItemId} are required (400), so no default hides a choice of money (decision #44). The
 * rest — no items, an item twice, a quantity outside the line, the name and the device code — is the
 * domain's, with its own code.
 */
public class SubmitTablePaymentRequest {

    private String customerName;

    private String customerDeviceId;

    @NotNull
    private Boolean includeServiceCharge;

    @Valid
    private List<Item> items;

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerDeviceId() {
        return customerDeviceId;
    }

    public void setCustomerDeviceId(String customerDeviceId) {
        this.customerDeviceId = customerDeviceId;
    }

    public Boolean getIncludeServiceCharge() {
        return includeServiceCharge;
    }

    public void setIncludeServiceCharge(Boolean includeServiceCharge) {
        this.includeServiceCharge = includeServiceCharge;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    /** Units of one item: {@code {tabItemId, quantity}}. */
    public static class Item {

        @NotBlank
        private String tabItemId;

        private Integer quantity;

        public String getTabItemId() {
            return tabItemId;
        }

        public void setTabItemId(String tabItemId) {
            this.tabItemId = tabItemId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }
}
