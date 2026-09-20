package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.MenuCategory;

/** A category as the administration screen reads it. */
public record MenuCategoryResponse(String id, String name, int displayOrder, boolean isActive) {

    public static MenuCategoryResponse from(MenuCategory category) {
        return new MenuCategoryResponse(
                category.id().value().toString(),
                category.name(),
                category.displayOrder(),
                category.isActive());
    }
}
