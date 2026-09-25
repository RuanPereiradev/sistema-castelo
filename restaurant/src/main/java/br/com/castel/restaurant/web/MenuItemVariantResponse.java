package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.MenuItemVariant;

/** A variant as the administration screen reads it, active or not. */
public record MenuItemVariantResponse(
        String id, String name, String price, int displayOrder, boolean isActive, boolean isAvailable) {

    public static MenuItemVariantResponse from(MenuItemVariant variant) {
        return new MenuItemVariantResponse(
                variant.id().value().toString(),
                variant.name(),
                variant.unitPrice().asString(),
                variant.displayOrder(),
                variant.isActive(),
                variant.isAvailable());
    }
}
