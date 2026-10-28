package br.com.castel.restaurant.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Body of {@code POST /public/tables/{orderingToken}/order-requests}: who the customer is (decision #40
 * of task qr-table-payment) and the lines they chose.
 *
 * <p>As in {@link AddTabItemRequest}, only the ids are checked here: an absent {@code menuItemId} or
 * {@code modifierId} answers 400. No lines, more than 20 and an item sold by weight are rules of the
 * domain and answer {@code INVALID_TABLE_ORDER_REQUEST}; quantity, variant, modifiers and instructions
 * are checked at confirmation (decision #20). There is no weight: the customer has no scale.
 */
public class SubmitTableOrderRequest {

    /** The name typed at the entrance; checked by the domain ({@code INVALID_CUSTOMER_NAME}). */
    private String customerName;

    /** The random code the phone keeps for its device; checked by the domain. */
    private String customerDeviceId;

    @Valid
    private List<Line> lines;

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

    public List<Line> getLines() {
        return lines;
    }

    public void setLines(List<Line> lines) {
        this.lines = lines;
    }

    /** One line: {@code {menuItemId, variantId?, quantity, modifiers, specialInstructions?}}. */
    public static class Line {

        @NotBlank
        private String menuItemId;

        private String variantId;

        private Integer quantity;

        @Valid
        private List<AddTabItemRequest.ModifierChoiceRequest> modifiers;

        private String specialInstructions;

        public String getMenuItemId() {
            return menuItemId;
        }

        public void setMenuItemId(String menuItemId) {
            this.menuItemId = menuItemId;
        }

        public String getVariantId() {
            return variantId;
        }

        public void setVariantId(String variantId) {
            this.variantId = variantId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public List<AddTabItemRequest.ModifierChoiceRequest> getModifiers() {
            return modifiers;
        }

        public void setModifiers(List<AddTabItemRequest.ModifierChoiceRequest> modifiers) {
            this.modifiers = modifiers;
        }

        public String getSpecialInstructions() {
            return specialInstructions;
        }

        public void setSpecialInstructions(String specialInstructions) {
            this.specialInstructions = specialInstructions;
        }
    }
}
