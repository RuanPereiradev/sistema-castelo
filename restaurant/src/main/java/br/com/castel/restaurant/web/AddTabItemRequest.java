package br.com.castel.restaurant.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Body of {@code POST /api/restaurant/tabs/{tabId}/items}.
 *
 * <p>Only the ids are checked here, because without them there is nothing to load: an absent
 * {@code menuItemId} or {@code modifierId} answers 400. Everything else — quantity, weight, variant,
 * the quantity of each modifier, the length of the instructions — is a rule of the domain and answers
 * its own code with 422.
 */
public class AddTabItemRequest {

    @NotBlank
    private String menuItemId;

    private String variantId;

    private Integer quantity;

    private Integer weightGrams;

    @Valid
    private List<ModifierChoiceRequest> modifiers;

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

    public Integer getWeightGrams() {
        return weightGrams;
    }

    public void setWeightGrams(Integer weightGrams) {
        this.weightGrams = weightGrams;
    }

    public List<ModifierChoiceRequest> getModifiers() {
        return modifiers;
    }

    public void setModifiers(List<ModifierChoiceRequest> modifiers) {
        this.modifiers = modifiers;
    }

    public String getSpecialInstructions() {
        return specialInstructions;
    }

    public void setSpecialInstructions(String specialInstructions) {
        this.specialInstructions = specialInstructions;
    }

    /** One modifier of the order: {@code {modifierId, quantity}}. */
    public static class ModifierChoiceRequest {

        @NotBlank
        private String modifierId;

        private Integer quantity;

        public String getModifierId() {
            return modifierId;
        }

        public void setModifierId(String modifierId) {
            this.modifierId = modifierId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }
}
