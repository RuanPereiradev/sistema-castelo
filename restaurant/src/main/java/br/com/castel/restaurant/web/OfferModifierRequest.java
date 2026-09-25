package br.com.castel.restaurant.web;

import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code PUT /api/restaurant/menu-items/{menuItemId}/modifiers/{modifierId}}.
 *
 * <p>Only presence is checked here; the range 1 to 99 is a rule of the domain and answers
 * {@code INVALID_MODIFIER_MAX_QUANTITY}.
 */
public class OfferModifierRequest {

    @NotNull
    private Integer maxQuantity;

    public Integer getMaxQuantity() {
        return maxQuantity;
    }

    public void setMaxQuantity(Integer maxQuantity) {
        this.maxQuantity = maxQuantity;
    }
}
