package br.com.castel.restaurant.application;

import br.com.castel.restaurant.api.MenuItemId;
import br.com.castel.restaurant.domain.MenuItemVariantId;
import br.com.castel.restaurant.domain.ModifierId;
import java.util.List;

/**
 * An order of one item as the route receives it: ids still to be loaded, numbers still to be
 * checked. {@link TabService#addItem} loads the menu item and the modifiers and hands the aggregate a
 * {@link br.com.castel.restaurant.domain.TabItemOrder}.
 *
 * @param modifiers the modifiers chosen, in the order they came; null is read as none
 */
public record AddTabItemCommand(
        MenuItemId menuItemId,
        MenuItemVariantId variantId,
        Integer quantity,
        Integer weightGrams,
        List<ChosenModifier> modifiers,
        String specialInstructions) {

    public AddTabItemCommand {
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
    }

    /** A modifier by its id, with how many of it go on each unit of the item. */
    public record ChosenModifier(ModifierId modifierId, int quantity) {
    }
}
