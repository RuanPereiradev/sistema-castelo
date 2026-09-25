package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.MenuItem;
import br.com.castel.restaurant.domain.Modifier;
import br.com.castel.restaurant.domain.ModifierId;
import java.util.List;
import java.util.Map;

/**
 * An item as the administration screen reads it: everything, including what only the operation
 * cares about.
 *
 * <p>{@code price} is a decimal string, and {@code soldByWeight} says whether it is the price of one
 * unit or of one kilo. It is the price of the item itself, the one that is edited; the "from" price
 * shown on the public menu, calculated from the variants, is not repeated here.
 *
 * <p>{@code variants} lists every variant, active or not, in display order. {@code modifiers} lists
 * the links to the catalog with the name, price and state of each modifier, whether or not it is
 * active there. {@code catalog} holds every modifier of the property, active or not, by id.
 */
public record MenuItemResponse(
        String id,
        String categoryId,
        String name,
        String description,
        String prepStation,
        boolean soldByWeight,
        String price,
        boolean serviceChargeEligible,
        boolean isAvailable,
        boolean isActive,
        int displayOrder,
        List<AvailabilityWindowResponse> availabilityWindows,
        List<MenuItemVariantResponse> variants,
        List<MenuItemModifierResponse> modifiers) {

    public static MenuItemResponse from(MenuItem item, Map<ModifierId, Modifier> catalog) {
        return new MenuItemResponse(
                item.id().value().toString(),
                item.menuCategoryId().value().toString(),
                item.name(),
                item.description(),
                item.prepStation().name(),
                item.soldByWeight(),
                item.price().asString(),
                item.serviceChargeEligible(),
                item.isAvailable(),
                item.isActive(),
                item.displayOrder(),
                item.availabilityWindows().stream().map(AvailabilityWindowResponse::from).toList(),
                item.variants().stream().map(MenuItemVariantResponse::from).toList(),
                item.modifiers().stream()
                        .map(link -> MenuItemModifierResponse.from(link, catalog.get(link.modifierId())))
                        .toList());
    }
}
