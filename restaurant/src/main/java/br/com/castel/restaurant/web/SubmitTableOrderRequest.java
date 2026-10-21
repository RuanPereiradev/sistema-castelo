package br.com.castel.restaurant.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Body of {@code POST /public/tables/{orderingToken}/order-requests}: the lines the customer chose.
 *
 * <p>As in {@link AddTabItemRequest}, only the ids are checked here: an absent {@code menuItemId} or
 * {@code modifierId} answers 400. No lines, more than 20 and an item sold by weight are rules of the
 * domain and answer {@code INVALID_TABLE_ORDER_REQUEST}; quantity, variant, modifiers and instructions
 * are checked at confirmation (decision #20). There is no weight: the customer has no scale.
 */
public class SubmitTableOrderRequest {

    @Valid
    private List<Line> lines;

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
