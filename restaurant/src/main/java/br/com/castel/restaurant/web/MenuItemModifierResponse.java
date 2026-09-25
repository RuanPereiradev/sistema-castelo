package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.MenuItemModifier;
import br.com.castel.restaurant.domain.Modifier;

/**
 * A modifier offered on an item, as the administration screen reads it: the link, with the name,
 * price and state of the modifier alongside, so the screen needs no second call (decision #26).
 */
public record MenuItemModifierResponse(String modifierId, String name, String price, int maxQuantity, boolean isActive) {

    public static MenuItemModifierResponse from(MenuItemModifier link, Modifier modifier) {
        return new MenuItemModifierResponse(
                link.modifierId().value().toString(),
                modifier.name(),
                modifier.price().asString(),
                link.maxQuantity(),
                modifier.isActive());
    }
}
