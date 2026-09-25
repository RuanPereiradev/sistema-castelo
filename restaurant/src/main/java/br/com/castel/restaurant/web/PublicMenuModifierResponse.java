package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.MenuItemModifier;
import br.com.castel.restaurant.domain.Modifier;

/**
 * A modifier offered on an item, as anyone reading the public menu sees it. {@code id} is the id of
 * the modifier. A modifier at no cost reads {@code "0.00"}.
 */
public record PublicMenuModifierResponse(String id, String name, String price, int maxQuantity) {

    public static PublicMenuModifierResponse from(Modifier modifier, MenuItemModifier link) {
        return new PublicMenuModifierResponse(
                modifier.id().value().toString(), modifier.name(), modifier.price().asString(), link.maxQuantity());
    }
}
