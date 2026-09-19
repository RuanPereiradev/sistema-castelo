package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.MenuItem;
import java.util.List;

/**
 * An item as the administration screen reads it: everything, including what only the operation
 * cares about.
 *
 * <p>{@code price} is a decimal string, and {@code soldByWeight} says whether it is the price of one
 * unit or of one kilo.
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
        List<AvailabilityWindowResponse> availabilityWindows) {

    public static MenuItemResponse from(MenuItem item) {
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
                item.availabilityWindows().stream().map(AvailabilityWindowResponse::from).toList());
    }
}
