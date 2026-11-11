package br.com.castel.restaurant.web;

import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code PATCH /api/restaurant/menu-items/{menuItemId}/requires-preparation}. Required: a
 * missing value is refused instead of read as false, which would take the item off the kitchen
 * display with nobody deciding it.
 */
public class RequiresPreparationRequest {

    @NotNull
    private Boolean requiresPreparation;

    public Boolean getRequiresPreparation() {
        return requiresPreparation;
    }

    public void setRequiresPreparation(Boolean requiresPreparation) {
        this.requiresPreparation = requiresPreparation;
    }
}
