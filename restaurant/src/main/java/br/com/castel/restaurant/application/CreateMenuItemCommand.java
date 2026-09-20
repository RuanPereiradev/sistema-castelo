package br.com.castel.restaurant.application;

import br.com.castel.restaurant.api.MenuCategoryId;
import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.sharedkernel.Money;

/**
 * What it takes to put a new item on the menu.
 *
 * <p>{@code price} is read as a unit price or as a price per kilo depending on {@code soldByWeight}
 * — the aggregate refuses to carry both, and so does the database.
 */
public record CreateMenuItemCommand(
        MenuCategoryId categoryId,
        String name,
        String description,
        PrepStation prepStation,
        boolean soldByWeight,
        Money price,
        boolean serviceChargeEligible) {
}
